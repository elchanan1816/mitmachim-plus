package community.mitmachim.nativeapp

import java.util.Locale

// All swatches use a dark enough background for a white initial in either theme.
val avatarSwatches=listOf(0xFF007F89L,0xFF235DA4L,0xFF764DA3L,0xFFAC405FL,0xFF855F19L,0xFF496879L,0xFF356C65L,0xFF815AC0L)
fun avatarColorIndex(name:String,uid:Int=0):Int {
 val key=if(uid>0)"uid:$uid"else name.trim().lowercase(Locale.ROOT)
 return Math.floorMod(key.hashCode(),avatarSwatches.size)
}
fun avatarInitial(name:String)=name.firstOrNull{it.isLetterOrDigit()}?.toString()?:"?"
