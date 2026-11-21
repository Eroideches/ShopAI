package com.eroideches.shopai.core.api

/** Descrizione di una richiesta HTTP: il modulo core la prepara, l'app la esegue (OkHttp). */
data class HttpSpec(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

/** Esito di una verifica chiavi o di una chiamata API, con un messaggio leggibile. */
data class ApiError(val message: String)
