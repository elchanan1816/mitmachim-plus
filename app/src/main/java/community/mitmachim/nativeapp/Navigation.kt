package community.mitmachim.nativeapp

fun resolveCategoryShortcut(route:Route,categories:List<Category>):Route {
 if(route.kind!="category")return route
 val directory=flatten(categories).associateBy{it.id}
 val visited=mutableSetOf<Int>()
 var target=route
 while(target.kind=="category"){
  if(!visited.add(target.cid))return route
  val link=directory[target.cid]?.link?.takeIf{it.isNotBlank()}?:return target
  postIdFromLink(link)?.let{return Route("post",pid=it)}
  target=routeFromLink(link)?:return safeLink(link)?.takeIf{isForumUrl(it)&&it.host=="mitmachim.top"}?.let{Route("browser",query=it.toString())}?:target
 }
 return target
}

fun categoryShortcut(category:Category):Route=resolveCategoryShortcut(Route("category",cid=category.id),listOf(category))

fun routeFromLink(value:String):Route? {
 val url=safeLink(value)?.takeIf{isForumUrl(it)&&it.host=="mitmachim.top"}?:return null
 val parts=url.pathSegments
 if(parts.firstOrNull()=="user"&&!parts.getOrNull(1).isNullOrBlank())return Route("user",query=parts[1])
 val id=parts.getOrNull(1)?.toIntOrNull()?.takeIf{it>0}?:return null
 val page=url.queryParameter("page")?.toIntOrNull()?.coerceAtLeast(1)?:1
 return when(parts.firstOrNull()) {
  "topic" -> Route("topic",tid=id,page=page,index=parts.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(1)?:if(page==1)1 else 0)
  "category" -> Route("category",cid=id,page=page)
  else -> null
 }
}
fun postIdFromLink(value:String):Int? {
 val url=safeLink(value)?.takeIf{isForumUrl(it)&&it.host=="mitmachim.top"}?:return null
 if(url.pathSegments.firstOrNull()!="post")return null
 return url.pathSegments.getOrNull(1)?.toIntOrNull()?.takeIf{it>0}
}
