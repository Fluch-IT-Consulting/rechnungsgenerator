package de.fluchitconsulting.rechnungsgenerator.toolchain

/** Offen, damit ein Kommando ein Scheitern am Typ erkennt und nicht am Wortlaut (#386). */
open class ToolchainException(message: String, cause: Throwable? = null) : Exception(message, cause)
