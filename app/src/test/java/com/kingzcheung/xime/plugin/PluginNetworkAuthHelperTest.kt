package com.kingzcheung.xime.plugin

import com.kingzcheung.xime.plugin.core.model.PluginInfo
import com.kingzcheung.xime.plugin.core.model.PluginSource
import com.kingzcheung.xime.plugin.core.model.TrustLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 11-03：填完密钥直接点「测试接口」被拦成「联网被拒绝」还跳页。市场装的和内置的插件，填的接口地址直接放行。 */
class PluginNetworkAuthHelperTest {

    private fun info(source: PluginSource, trust: TrustLevel, allowCustom: Boolean = true) = PluginInfo(
        id = "p", name = "p", iconResId = 0, versionCode = 1, versionName = "1",
        path = "", description = "", type = "tool",
        source = source, trustLevel = trust, allowCustomHosts = allowCustom,
    )

    @Test
    fun `市场装的和内置的插件，填的地址直接放行`() {
        assertTrue(PluginNetworkAuthHelper.trustsConfiguredHosts(info(PluginSource.REMOTE, TrustLevel.THIRD_PARTY)))
        assertTrue(PluginNetworkAuthHelper.trustsConfiguredHosts(info(PluginSource.ASSET, TrustLevel.TRUSTED)))
        assertTrue(PluginNetworkAuthHelper.trustsConfiguredHosts(info(PluginSource.SYSTEM, TrustLevel.TRUSTED)))
    }

    @Test
    fun `文件导入的非官方插件、不收自定义地址的插件仍要授权`() {
        assertFalse(PluginNetworkAuthHelper.trustsConfiguredHosts(info(PluginSource.FILE, TrustLevel.THIRD_PARTY)))
        assertFalse(PluginNetworkAuthHelper.trustsConfiguredHosts(info(PluginSource.REMOTE, TrustLevel.THIRD_PARTY, allowCustom = false)))
        assertFalse(PluginNetworkAuthHelper.trustsConfiguredHosts(null))
    }
}
