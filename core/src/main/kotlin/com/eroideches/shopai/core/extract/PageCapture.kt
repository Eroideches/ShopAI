package com.eroideches.shopai.core.extract

import kotlinx.serialization.Serializable

@Serializable
data class NetworkBody(val url: String, val body: String)

/** Ciò che si legge da una pagina: HTML (già renderizzato se viene dalla WebView) + JSON catturati dalla rete. */
@Serializable
data class PageCapture(
    val url: String,
    val html: String,
    val network: List<NetworkBody> = emptyList(),
)
