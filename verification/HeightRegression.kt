import android.content.*
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.service.*

fun context(landscape: Boolean=false) = Context(Resources(Configuration(if(landscape)840 else 411, if(landscape)411 else 840)))
fun same(a: Any?,b: Any?) { check(a==b) {"expected=$a actual=$b"} }
fun main() {
 var passed=0
 fun case(name: String, block: () -> Unit) { block(); passed++; println("PASS $name") }
 case("portrait: floating minimum must not change fixed 320") {
  val c=context(); SettingsPreferences.setKeyboardHeightDp(c,320,false)
  KeyboardHeightProfiles.save(c,true,false,180,840)
  same(320,KeyboardHeightProfiles.fixed(c,false)); same(180,KeyboardHeightProfiles.floating(c,false,840))
 }
 case("landscape: floating minimum must not change fixed 270") {
  val c=context(true); SettingsPreferences.setKeyboardHeightDp(c,270,true)
  KeyboardHeightProfiles.save(c,true,true,130,411)
  same(270,KeyboardHeightProfiles.fixed(c,true)); same(130,KeyboardHeightProfiles.floating(c,true,411))
 }
 case("portrait: fixed confirm does not change floating height") {
  val c=context(); KeyboardHeightProfiles.save(c,true,false,200,840)
  KeyboardHeightProfiles.save(c,false,false,310,840)
  same(200,KeyboardHeightProfiles.floating(c,false,840)); same(310,KeyboardHeightProfiles.fixed(c,false))
 }
 case("portrait and landscape floating keys are independent") {
  val c=context(); KeyboardHeightProfiles.save(c,true,false,220,840)
  c.resources.configuration.screenWidthDp=840; c.resources.configuration.screenHeightDp=411
  KeyboardHeightProfiles.save(c,true,true,145,411)
  same(220,c.prefs.getInt("floating_height_dp",0)); same(145,c.prefs.getInt("floating_height_dp_landscape",0))
 }
 case("fixed lower bound is enforced when reading portrait preferences") {
  val c=context(); c.prefs.values["keyboard_height_dp"]=130
  same(KeyboardHeightProfiles.fixedBounds(c,false).first,KeyboardHeightProfiles.fixed(c,false))
 }
 case("fixed lower bound is enforced when confirming portrait") {
  val c=context(); val h=KeyboardHeightProfiles.save(c,false,false,1,840)
  same(KeyboardHeightProfiles.fixedBounds(c,false).first,h)
 }
 case("fixed lower bound is enforced when confirming landscape") {
  val c=context(true); val h=KeyboardHeightProfiles.save(c,false,true,130,411)
  same(KeyboardHeightProfiles.fixedBounds(c,true).first,h)
 }
 case("legacy corrupted fixed value is backed up and restored to default") {
  val c=context(); c.prefs.values.putAll(mapOf("keyboard_height_dp" to 180,"floating_width_dp" to 260))
  KeyboardHeightProfiles.migrateLegacy(c,false,840)
  same(180,c.prefs.getInt("keyboard_height_dp_before_height_profiles",0))
  same(294,c.prefs.getInt("keyboard_height_dp",0)); same(180,c.prefs.getInt("floating_height_dp",0))
 }
 case("legacy valid custom fixed height is preserved exactly") {
  val c=context(); c.prefs.values.putAll(mapOf("keyboard_height_dp" to 319,"floating_width_dp" to 300))
  KeyboardHeightProfiles.migrateLegacy(c,false,840)
  same(319,c.prefs.getInt("keyboard_height_dp",0))
 }
 case("migration is idempotent and preserves later user settings") {
  val c=context(); c.prefs.values["keyboard_height_dp"]=180
  KeyboardHeightProfiles.migrateLegacy(c,false,840)
  KeyboardHeightProfiles.save(c,false,false,333,840)
  val before=c.prefs.values.toMap(); KeyboardHeightProfiles.migrateLegacy(c,false,840)
  same(before,c.prefs.values)
 }
 case("migration leaves unrelated theme and dictionary keys intact") {
  val c=context(); c.prefs.values.putAll(mapOf("keyboard_height_dp" to 130,"keyboard_theme" to "ocean", "schema" to "t9"))
  KeyboardHeightProfiles.migrateLegacy(c,false,840)
  same("ocean",c.prefs.values["keyboard_theme"]); same("t9",c.prefs.values["schema"])
 }
 case("migration callbacks observe complete transaction") {
  val c=context(); c.prefs.values["keyboard_height_dp"]=180
  c.prefs.onChange={ same(true,c.prefs.getBoolean("height_profiles_v1",false)); same(294,c.prefs.getInt("keyboard_height_dp",0)); KeyboardHeightProfiles.migrateLegacy(c,false,840) }
  KeyboardHeightProfiles.migrateLegacy(c,false,840)
 }
 case("fresh install does not create a fake fixed preference") {
  val c=context(); KeyboardHeightProfiles.migrateLegacy(c,false,840)
  check(!c.prefs.contains("keyboard_height_dp")); check(!c.prefs.contains("floating_height_dp"))
 }
 case("existing new floating preference survives legacy migration") {
  val c=context(); c.prefs.values.putAll(mapOf("keyboard_height_dp" to 180,"floating_height_dp" to 250, "floating_mode" to true))
  KeyboardHeightProfiles.migrateLegacy(c,false,840); same(250,c.prefs.getInt("floating_height_dp",0))
 }
 case("actual confirm callback: floating save keeps fixed preference and padding") {
  val c=XimeInputMethodService(); c.prefs.values.putAll(mapOf("keyboard_height_dp" to 320,"keyboard_bottom_padding_dp" to 14))
  c.onConfirm(180,0,true,0.8f)
  same(320,c.prefs.getInt("keyboard_height_dp",0)); same(180,c.prefs.getInt("floating_height_dp",0)); same(14,c.prefs.getInt("keyboard_bottom_padding_dp",0))
  same(false,c.uiState.value.showKeyboardResize); same(true,c.uiState.value.isFloatingMode); same(0,c.uiState.value.resizePreviewHeightDp)
 }
 case("actual confirm callback: fixed save leaves floating size and position untouched") {
  val c=XimeInputMethodService(); c.prefs.values.putAll(mapOf("floating_height_dp" to 180,"floating_width_dp" to 260,"floating_offset_x" to 18,"floating_offset_y" to 64))
  c.onConfirm(320,20,false,1f)
  same(180,c.prefs.getInt("floating_height_dp",0)); same(260,c.prefs.getInt("floating_width_dp",0)); same(18,c.prefs.getInt("floating_offset_x",0)); same(64,c.prefs.getInt("floating_offset_y",0))
  same(320,c.prefs.getInt("keyboard_height_dp",0)); same(false,c.uiState.value.isFloatingMode)
 }
 case("actual confirm callback: preference listener cannot erase preview offsets") {
  val c=XimeInputMethodService(); c.prefs.values["keyboard_height_dp"]=320
  c.prefs.onChange={ c.uiState.value=c.uiState.value.copy(floatingOffsetX=0,floatingOffsetY=0,resizePreviewWidthDp=0) }
  c.onConfirm(180,0,true,1f)
  same(12,c.prefs.getInt("floating_offset_x",0)); same(72,c.prefs.getInt("floating_offset_y",0)); same(260,c.prefs.getInt("floating_width_dp",0))
  same(12,c.uiState.value.floatingOffsetX); same(72,c.uiState.value.floatingOffsetY)
 }
 case("actual confirm callback: landscape saves separate floating key") {
  val c=XimeInputMethodService(true); c.prefs.values["keyboard_height_dp_landscape"]=270
  c.onConfirm(130,0,true,1f)
  same(270,c.prefs.getInt("keyboard_height_dp_landscape",0)); same(130,c.prefs.getInt("floating_height_dp_landscape",0))
 }
 case("close-open and dock read the appropriate saved profile") {
  val c=context(); SettingsPreferences.setKeyboardHeightDp(c,321,false)
  KeyboardHeightProfiles.save(c,true,false,180,840)
  val reopened=Context(c.resources,c.prefs)
  same(180,KeyboardHeightProfiles.selected(reopened,true,false,840)); same(321,KeyboardHeightProfiles.selected(reopened,false,false,840))
 }
 case("repeated floating confirmations never overwrite either fixed key") {
  val c=context(); c.prefs.values.putAll(mapOf("keyboard_height_dp" to 320,"keyboard_height_dp_landscape" to 270))
  repeat(200){ KeyboardHeightProfiles.save(c,true,it%2==0,130+it,if(it%2==0)411 else 840) }
  same(320,c.prefs.getInt("keyboard_height_dp",0)); same(270,c.prefs.getInt("keyboard_height_dp_landscape",0))
 }
 println("RESULT $passed tests, 0 failures")
}
