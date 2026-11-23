package dev.sanghyuck.ratefanout.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

inline fun <reified T> T.logger() = LoggerFactory.getLogger(T::class.java)!!

// JDBC는 블로킹이라 이벤트 루프 스레드에서 돌리면 안 된다
suspend fun <T> blockingIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }
