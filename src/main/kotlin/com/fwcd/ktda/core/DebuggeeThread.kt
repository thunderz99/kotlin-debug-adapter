package com.fwcd.ktda.core

import com.fwcd.ktda.core.stack.StackTrace

interface DebuggeeThread {
	val id: Long
	
	fun pause()
	
	fun resume()
	
	fun stepOver()
	
	fun stepInto()
	
	fun stepOut()
	
	fun stackTrace(): StackTrace
}