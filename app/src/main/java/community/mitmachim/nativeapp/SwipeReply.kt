package community.mitmachim.nativeapp

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.abs

/** A horizontal-only gesture: vertical scroll/long selection wins before horizontal touch slop. */
@Composable fun Modifier.swipeToReply(enabled:Boolean,onDistance:(Float)->Unit,onReply:()->Unit):Modifier {
 val latestReply by rememberUpdatedState(onReply)
 val latestDistance by rememberUpdatedState(onDistance)
 val density=LocalDensity.current.density
 val haptic=LocalHapticFeedback.current
 val configuration=LocalViewConfiguration.current
 return if(!enabled)this else pointerInput(enabled,density){
  val limit=84f*density
  awaitEachGesture{
   val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
   var distance=0f;var horizontal=false;var released=false
   try{
    while(true){
     // Initial pass also sees native HTML TextView events before AndroidView consumes them.
     val event=awaitPointerEvent(PointerEventPass.Initial)
     if(event.changes.count{it.pressed}>1)break
     val change=event.changes.firstOrNull{it.id==down.id}?:break
     if(!change.pressed){released=horizontal;if(horizontal)change.consume();break}
     val dx=change.position.x-down.position.x;val dy=change.position.y-down.position.y
     if(!horizontal){
      if(change.uptimeMillis-down.uptimeMillis>=configuration.longPressTimeoutMillis)break
      if(abs(dy)>configuration.touchSlop&&abs(dx)<=abs(dy)*1.5f)break
      horizontal=abs(dx)>configuration.touchSlop&&abs(dx)>abs(dy)*1.5f
     }
     if(horizontal){distance=dx.coerceIn(-limit,limit);change.consume();latestDistance(distance)}
    }
   }finally{latestDistance(0f)}
   if(released&&replySwipeAccepted(distance/density)){haptic.performHapticFeedback(HapticFeedbackType.LongPress);latestReply()}
  }
 }
}
fun replySwipeAccepted(distanceDp:Float)=distanceDp.isFinite()&&abs(distanceDp)>=64f
fun canSwipeReply(canReply:Boolean,deleted:Boolean,sending:Boolean)=canReply&&!deleted&&!sending
