package com.neilturner.aerialviews.testutils

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the app's resource files straight from the source tree, so unit tests can check how
 * resources fit together without needing android. Paths are relative to src/main/res.
 *
 * Gradle runs unit tests with the app module as the working directory, the same assumption
 * TestConfigSampleTest makes for docs/testing/configs.
 */
internal object ResourceXml {
    private val resDir: File =
        listOf("src/main/res", "app/src/main/res")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?: error("Could not find src/main/res from ${File("").absolutePath}")

    fun file(path: String): File = File(resDir, path)

    fun root(path: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file(path))
            .documentElement

    fun children(
        parent: Element,
        tag: String,
    ): List<Element> =
        (0 until parent.childNodes.length)
            .map { parent.childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE && it.nodeName == tag }
            .map { it as Element }

    /** Name and text of every string in the file */
    fun strings(path: String): Map<String, String> =
        children(root(path), "string").associate { it.getAttribute("name") to it.textContent.trim() }

    /** Names of the strings in the file which should be translated */
    fun translatableStringNames(path: String): Set<String> =
        children(root(path), "string")
            .filter { it.getAttribute("translatable") != "false" }
            .map { it.getAttribute("name") }
            .toSet()

    /** Names of all strings declared by any file for the default language */
    fun defaultStringNames(): Set<String> =
        file("values")
            .listFiles { candidate -> candidate.extension == "xml" }
            .orEmpty()
            .flatMap { strings("values/${it.name}").keys }
            .toSet()

    fun stringArray(
        path: String,
        name: String,
    ): List<String> {
        val array =
            children(root(path), "string-array").firstOrNull { it.getAttribute("name") == name }
                ?: error("No string-array '$name' in $path")
        return children(array, "item").map { it.textContent.trim() }
    }

    /** Every element in the file, at any depth, which has the attribute, such as "app:key" */
    fun elementsWithAttribute(
        path: String,
        attribute: String,
    ): List<Element> {
        val all = root(path).getElementsByTagName("*")
        return (0 until all.length)
            .map { all.item(it) as Element }
            .filter { it.hasAttribute(attribute) }
    }

    /** The preference with the given key in a preference screen file */
    fun preference(
        path: String,
        key: String,
    ): Element =
        elementsWithAttribute(path, "app:key").firstOrNull { it.getAttribute("app:key") == key }
            ?: error("No preference with key '$key' in $path")
}
