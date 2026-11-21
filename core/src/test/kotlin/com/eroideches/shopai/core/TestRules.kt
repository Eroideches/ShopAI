package com.eroideches.shopai.core

import com.eroideches.shopai.core.rules.RulesFile
import com.eroideches.shopai.core.rules.SiteRule
import java.io.File

object TestRules {
    val file: RulesFile by lazy {
        val f = listOf(File("../rules/sites.json"), File("rules/sites.json"), File("../../rules/sites.json")).first { it.exists() }
        RulesFile.parse(f.readText())
    }

    fun site(id: String): SiteRule = file.site(id)!!

    fun resource(name: String): String = TestRules::class.java.getResource("/$name")!!.readText()
}
