package io.github.dimidrol

interface WorkloadSession {
    val name: String
    val type: WorkloadType

    fun start()

    fun stop(): WorkloadReport
}
