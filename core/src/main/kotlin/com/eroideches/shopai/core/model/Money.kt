package com.eroideches.shopai.core.model

import kotlinx.serialization.Serializable

/** Importo in euro, in centesimi. ShopAI non gestisce altre valute: un prezzo non in € viene scartato, mai convertito. */
@Serializable
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    override fun compareTo(other: Money) = cents.compareTo(other.cents)

    val euros: Double get() = cents / 100.0

    /** Formato italiano: "1.299,90 €". */
    fun format(): String {
        val abs = kotlin.math.abs(cents)
        val intPart = (abs / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val dec = (abs % 100).toString().padStart(2, '0')
        return (if (cents < 0) "-" else "") + "$intPart,$dec €"
    }

    override fun toString() = format()

    companion object {
        val ZERO = Money(0)
        fun ofEuros(value: Double) = Money(Math.round(value * 100))
    }
}
