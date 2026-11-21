package com.ridecomp.platform.ble

import com.ridecomp.core.engine.sensors.SensorKind
import java.util.Locale
import java.util.UUID

/** Bluetooth SIG 16-bit UUIDs used by RideComp (04-INTEGRATIONS §A), expanded on the base UUID. */
object BleUuids {
    private const val BASE_FORMAT = "0000%04x-0000-1000-8000-00805f9b34fb"

    private fun of(shortUuid: Int): UUID = UUID.fromString(String.format(Locale.ROOT, BASE_FORMAT, shortUuid))

    val HEART_RATE_SERVICE: UUID = of(0x180D)
    val HEART_RATE_MEASUREMENT: UUID = of(0x2A37)
    val CSC_SERVICE: UUID = of(0x1816)
    val CSC_MEASUREMENT: UUID = of(0x2A5B)
    val CYCLING_POWER_SERVICE: UUID = of(0x1818)
    val CYCLING_POWER_MEASUREMENT: UUID = of(0x2A63)

    /** Fitness Machine (smart trainer, T9.1): Indoor Bike Data notifications, Control Point for ERG. */
    val FITNESS_MACHINE_SERVICE: UUID = of(0x1826)
    val INDOOR_BIKE_DATA: UUID = of(0x2AD2)
    val FITNESS_MACHINE_CONTROL_POINT: UUID = of(0x2AD9)
    val BATTERY_SERVICE: UUID = of(0x180F)
    val BATTERY_LEVEL: UUID = of(0x2A19)

    /**
     * GloryFit watch protocol (Aolon GTR 3, T12.5b, 04-INTEGRATIONS §A.5a): vendor UUIDs on the same base,
     * commands to 0x33F1, heart rate notified on 0x33F2.
     */
    val GLORYFIT_SERVICE: UUID = of(0x55FF)
    val GLORYFIT_COMMAND: UUID = of(0x33F1)
    val GLORYFIT_DATA: UUID = of(0x33F2)

    /** Client Characteristic Configuration descriptor, written to enable notifications. */
    val CCCD: UUID = of(0x2902)

    val SERVICE_BY_KIND: Map<SensorKind, UUID> = mapOf(
        SensorKind.HEART_RATE to HEART_RATE_SERVICE,
        SensorKind.SPEED_CADENCE to CSC_SERVICE,
        SensorKind.POWER to CYCLING_POWER_SERVICE,
    )

    /** Measurement characteristic per service, notified once subscribed. */
    val MEASUREMENT_BY_SERVICE: Map<UUID, UUID> = mapOf(
        HEART_RATE_SERVICE to HEART_RATE_MEASUREMENT,
        CSC_SERVICE to CSC_MEASUREMENT,
        CYCLING_POWER_SERVICE to CYCLING_POWER_MEASUREMENT,
    )

    fun kindOf(serviceUuid: UUID): SensorKind? = SERVICE_BY_KIND.entries.firstOrNull { it.value == serviceUuid }?.key
}
