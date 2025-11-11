package com.insidepacer.health

import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectRepoImplTest {
    @Test
    fun `sdk available is installed`() {
        val availability = mapSdkStatusToAvailability(HealthConnectClient.SDK_AVAILABLE)
        assertEquals(HcAvailability.SUPPORTED_INSTALLED, availability)
    }

    @Test
    fun `sdk requires install is supported not installed`() {
        val installStatuses = listOf(
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_INSTALLATION_REQUIRED,
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_DISABLED,
        )
        installStatuses.forEach { status ->
            val availability = mapSdkStatusToAvailability(status)
            assertEquals(HcAvailability.SUPPORTED_NOT_INSTALLED, availability)
        }
    }

    @Test
    fun `unsupported statuses map to not supported`() {
        val unsupportedStatuses = listOf(
            HealthConnectClient.SDK_UNAVAILABLE,
            HealthConnectClient.SDK_UNAVAILABLE_APP_NOT_VERIFIED,
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_POLICY_RESTRICTION,
        )
        unsupportedStatuses.forEach { status ->
            val availability = mapSdkStatusToAvailability(status)
            assertEquals(HcAvailability.NOT_SUPPORTED, availability)
        }
    }
}
