package community.mitmachim.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Original vector doodles inspired by the supplied reference, not a remote wallpaper. */
@Composable fun ChatDoodleWallpaper(modifier:Modifier=Modifier){
 val dark=MaterialTheme.colorScheme.background.luminance()<.3f
 val paper=chatPaperColor(dark);val ink=chatDoodleColor(dark)
 Canvas(modifier){
  drawRect(paper)
  val step=82.dp.toPx();val stamp=54.dp.toPx()
  var row=-1;var y=-step
  while(y<size.height){
   var column=-1;var x=-step+if(row%2==0)0f else step*.33f
   while(x<size.width){
    val symbol=Math.floorMod(row*3+column,8)
    translate(x+8.dp.toPx(),y+8.dp.toPx()){
     rotate(listOf(-12f,9f,-5f,15f)[Math.floorMod(row+column,4)],Offset(stamp/2,stamp/2)){
      scale(stamp/64f,stamp/64f,Offset.Zero){drawChatDoodle(symbol,ink)}
     }
     drawCircle(ink,3.dp.toPx(),Offset(stamp+6.dp.toPx(),stamp*.25f),style=Stroke(1.dp.toPx()))
     drawCircle(ink,1.5.dp.toPx(),Offset(stamp*.2f,stamp+6.dp.toPx()))
    }
    x+=step;column++
   }
   y+=step;row++
  }
 }
}

private fun DrawScope.drawChatDoodle(symbol:Int,ink:Color){
 val stroke=Stroke(1.35f,cap=StrokeCap.Round,join=StrokeJoin.Round)
 fun line(x:Float,y:Float,endX:Float,endY:Float)=drawLine(ink,Offset(x,y),Offset(endX,endY),strokeWidth=1.35f,cap=StrokeCap.Round)
 fun path(block:Path.()->Unit)=drawPath(Path().apply(block),ink,style=stroke)
 when(symbol){
  0->{ // Speech bubble with dots.
   path{moveTo(13f,12f);lineTo(50f,12f);quadraticTo(56f,12f,56f,18f);lineTo(56f,38f);quadraticTo(56f,44f,50f,44f);lineTo(29f,44f);lineTo(16f,53f);lineTo(18f,44f);lineTo(13f,44f);quadraticTo(7f,44f,7f,38f);lineTo(7f,18f);quadraticTo(7f,12f,13f,12f);close()}
   listOf(21f,32f,43f).forEach{drawCircle(ink,2f,Offset(it,28f),style=stroke)}
  }
  1->{ // Paper plane.
   path{moveTo(7f,28f);lineTo(55f,9f);lineTo(43f,55f);lineTo(29f,40f);lineTo(18f,48f);lineTo(20f,34f);close()}
   line(20f,34f,55f,9f);line(29f,40f,55f,9f)
  }
  2->{ // Friendly smile.
   drawCircle(ink,22f,Offset(32f,32f),style=stroke)
   drawCircle(ink,1.6f,Offset(24f,26f));drawCircle(ink,1.6f,Offset(40f,26f))
   path{moveTo(21f,36f);cubicTo(26f,47f,39f,47f,44f,36f)}
  }
  3->{ // Open book.
   path{moveTo(32f,17f);quadraticTo(20f,9f,8f,14f);lineTo(8f,49f);quadraticTo(20f,44f,32f,52f);quadraticTo(44f,44f,56f,49f);lineTo(56f,14f);quadraticTo(44f,9f,32f,17f);lineTo(32f,52f)}
   line(14f,23f,25f,25f);line(14f,31f,25f,33f);line(39f,25f,50f,23f);line(39f,33f,50f,31f)
  }
  4->{ // Coffee cup, steam and saucer.
   path{moveTo(12f,27f);lineTo(45f,27f);lineTo(43f,43f);quadraticTo(41f,51f,29f,51f);quadraticTo(16f,51f,14f,43f);close();moveTo(45f,29f);cubicTo(61f,27f,59f,44f,44f,42f)}
   line(9f,56f,50f,56f)
   path{moveTo(24f,22f);cubicTo(13f,16f,30f,12f,23f,6f);moveTo(35f,21f);cubicTo(25f,15f,42f,12f,35f,5f)}
  }
  5->{ // Headphones.
   path{moveTo(10f,37f);lineTo(10f,31f);cubicTo(10f,1f,54f,1f,54f,31f);lineTo(54f,37f)}
   drawRoundRect(ink,Offset(8f,31f),Size(11f,22f),CornerRadius(4f),style=stroke)
   drawRoundRect(ink,Offset(45f,31f),Size(11f,22f),CornerRadius(4f),style=stroke)
   path{moveTo(49f,53f);quadraticTo(49f,59f,34f,59f)}
  }
  6->{ // Idea bulb and rays.
   path{moveTo(24f,43f);cubicTo(24f,37f,16f,34f,16f,25f);cubicTo(16f,5f,48f,5f,48f,25f);cubicTo(48f,34f,40f,37f,40f,43f);close()}
   line(25f,48f,39f,48f);line(27f,53f,37f,53f);line(32f,42f,32f,29f)
   line(8f,24f,3f,24f);line(56f,24f,61f,24f);line(32f,3f,32f,0f);line(13f,9f,9f,5f);line(51f,9f,55f,5f)
  }
  else->{ // Leaf and a small four-point sparkle.
   path{moveTo(15f,53f);cubicTo(4f,29f,19f,12f,50f,12f);cubicTo(53f,37f,37f,52f,15f,53f);close()}
   path{moveTo(12f,57f);quadraticTo(25f,34f,44f,18f)}
   line(25f,36f,23f,23f);line(25f,36f,39f,36f)
   path{moveTo(52f,43f);lineTo(54f,49f);lineTo(60f,51f);lineTo(54f,53f);lineTo(52f,59f);lineTo(50f,53f);lineTo(44f,51f);lineTo(50f,49f);close()}
  }
 }
}
