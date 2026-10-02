package community.mitmachim.nativeapp

enum class ThreadReplyAvailability { Compose, Locked, Login, Restricted }
fun threadReplyAvailability(locked:Boolean,canReply:Boolean,uid:Int)=when{
 locked->ThreadReplyAvailability.Locked
 canReply->ThreadReplyAvailability.Compose
 uid<=0->ThreadReplyAvailability.Login
 else->ThreadReplyAvailability.Restricted
}
