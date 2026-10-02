package io.openflux.android.core

import io.openflux.desktop.model.ExtraTransport
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.ReserveRoute
import io.openflux.desktop.model.TransportType
import kotlinx.serialization.json.*
import kotlin.test.*

class CoreSpecsTest {
    private val profile = Profile(
        id = "p", name = "Reserve", transport = TransportType.VYANDEX,
        value = "https://docs.example/yandex", secret = "f".repeat(64), session = true,
        extras = listOf(
            ExtraTransport(TransportType.DIRECT, "203.0.113.10:33445", priority = 110),
            ExtraTransport(TransportType.MAILRU, "https://docs.example/original", priority = 120),
            ExtraTransport(TransportType.VYANDEX, "https://docs.example/second", priority = 90),
        ),
    )

    private fun specs(p: Profile, exit: Boolean = false) = Json.parseToJsonElement(CoreSpecs.session(p, exit, 33445))

    @Test fun automaticModePassesEveryConfiguredCarrierToTheCore() {
        val rows = specs(profile).jsonArray
        assertEquals(listOf("vyandex", "direct", "mailru", "vyandex-2"), rows.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertEquals("203.0.113.10:33445", rows[1].jsonObject["params"]!!.jsonObject["dial"]!!.jsonPrimitive.content)
    }

    @Test fun yandexOnlyPassesTheOriginalContextAndOnlyYandexSpecs() {
        val root = specs(profile.copy(reserveRoute = ReserveRoute.Yandex)).jsonObject
        assertEquals("https://docs.example/original", root["context"]!!.jsonPrimitive.content)
        val rows = root["transports"]!!.jsonArray
        assertEquals(listOf("vyandex", "vyandex-2"), rows.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertEquals(listOf(100, 90), rows.map { it.jsonObject["priority"]!!.jsonPrimitive.int })
    }

    @Test fun explicitContextIsNeverReplacedByTheSelectedDocument() {
        val root = specs(profile.copy(context = "channel-original", reserveRoute = ReserveRoute.Yandex)).jsonObject
        assertEquals("channel-original", root["context"]!!.jsonPrimitive.content)
    }

    @Test fun exitStillListensForDirectWhileServingAllDocumentCarriers() {
        val rows = specs(profile.copy(reserveRoute = ReserveRoute.Yandex), exit = true).jsonArray
        assertTrue(rows.any { it.jsonObject["type"]!!.jsonPrimitive.content == "mailru" })
        val direct = rows.single { it.jsonObject["type"]!!.jsonPrimitive.content == "direct" }.jsonObject
        assertEquals("0.0.0.0:33445", direct["params"]!!.jsonObject["listen"]!!.jsonPrimitive.content)
        assertFalse("dial" in direct["params"]!!.jsonObject)
    }

    @Test fun unsupportedForcedRouteFailsBeforeStartingTheCore() {
        val invalid = profile.copy(transport = TransportType.DIRECT, value = "203.0.113.10:33445", extras = emptyList(), reserveRoute = ReserveRoute.Yandex)
        assertFailsWith<IllegalArgumentException> { specs(invalid) }
    }
}
