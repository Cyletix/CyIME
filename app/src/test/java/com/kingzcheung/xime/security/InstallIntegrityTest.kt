package com.kingzcheung.xime.security

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallIntegrityTest {

    private val official = InstallIntegrity.OFFICIAL_CERTS.first()
    private val other = "0".repeat(64)

    @Test
    fun authorKeyIsOfficial() {
        assertEquals(InstallIntegrity.Result.OFFICIAL, InstallIntegrity.classify(listOf(official), listOf(official)))
        assertEquals(InstallIntegrity.Result.OFFICIAL, InstallIntegrity.classify(listOf(official.uppercase()), emptyList()))
    }

    @Test
    fun resignedPackageIsForeign() {
        assertEquals(InstallIntegrity.Result.FOREIGN, InstallIntegrity.classify(listOf(other), listOf(other)))
    }

    @Test
    fun extraSignerNextToAuthorKeyIsForeign() {
        assertEquals(InstallIntegrity.Result.FOREIGN, InstallIntegrity.classify(listOf(official, other), emptyList()))
    }

    @Test
    fun rotatedFromAuthorKeyIsOfficial() {
        assertEquals(InstallIntegrity.Result.OFFICIAL, InstallIntegrity.classify(listOf(other), listOf(official, other)))
    }

    @Test
    fun unreadableSignatureDoesNotWarn() {
        assertEquals(InstallIntegrity.Result.UNKNOWN, InstallIntegrity.classify(emptyList(), emptyList()))
    }
}
