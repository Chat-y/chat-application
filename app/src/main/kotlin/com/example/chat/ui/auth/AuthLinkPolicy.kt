package com.example.chat.ui.auth

/** One parser for camera and app links. A parsed workspace is not a verified invitation. */
object AuthLinkPolicy {
    class Parameters(val kind:String,val workspace:String?,val token:String?) {override fun toString()="AuthLinkParameters(redacted)"}
    fun parse(raw:String):Parameters? = runCatching {
        if(raw.length>8192) return null
        val uri=java.net.URI(raw)
        if(uri.scheme!="chaty" || uri.userInfo!=null || uri.fragment!=null || uri.port!=-1 || !uri.path.isNullOrEmpty()) return null
        val params=linkedMapOf<String,String>()
        uri.rawQuery.orEmpty().split('&').filter {it.isNotBlank()}.forEach {
            val pair=it.split('=',limit=2)
            if(pair.size!=2) return null
            val key=java.net.URLDecoder.decode(pair[0],"UTF-8")
            if(key in params) return null
            params[key]=java.net.URLDecoder.decode(pair[1],"UTF-8")
        }
        val workspace=params["workspace"]?.takeIf {it.matches(Regex("[A-Za-z0-9_-]{1,128}"))}
        val token=params["token"]?.takeIf {it.length in 16..4096 && it.none {ch->ch.isISOControl() || ch.isWhitespace()}}
        when(uri.host) {
            "workspace"->workspace?.let {Parameters("workspace",it,null)}
            "verify-email"->token?.let {Parameters("verify-email",null,it)}
            "invite"->if(token!=null) Parameters("invite",workspace,token) else null
            else->null
        }
    }.getOrNull()
}
