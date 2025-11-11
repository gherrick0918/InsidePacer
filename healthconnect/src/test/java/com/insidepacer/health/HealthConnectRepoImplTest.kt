package com.insidepacer.health

import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_APP_NOT_VERIFIED
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_DISABLED
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_INSTALLATION_REQUIRED
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_POLICY_RESTRICTION
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectRepoImplTest {
    @Test
    fun `sdk available is installed`() {
        val availability = mapSdkStatusToAvailability(SDK_AVAILABLE)
        assertEquals(HcAvailability.SUPPORTED_INSTALLED, availability)
    }

    @Test
    fun `sdk requires install is supported not installed`() {
        val installStatuses = listOf(
            SDK_UNAVAILABLE_PROVIDER_INSTALLATION_REQUIRED,
            SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
            SDK_UNAVAILABLE_PROVIDER_DISABLED,
        )
        installStatuses.forEach { status ->
            val availability = mapSdkStatusToAvailability(status)
            assertEquals(HcAvailability.SUPPORTED_NOT_INSTALLED, availability)
        }
    }

    @Test
    fun `unsupported statuses map to not supported`() {
        val unsupportedStatuses = listOf(
            SDK_UNAVAILABLE,
            SDK_UNAVAILABLE_APP_NOT_VERIFIED,
            SDK_UNAVAILABLE_PROVIDER_POLICY_RESTRICTION,
        )
        unsupportedStatuses.forEach { status ->
            val availability = mapSdkStatusToAvailability(status)
            assertEquals(HcAvailability.NOT_SUPPORTED, availability)
        }
    }
}
