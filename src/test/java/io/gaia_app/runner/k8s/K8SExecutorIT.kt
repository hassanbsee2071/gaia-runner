package io.gaia_app.runner.k8s

import io.gaia_app.runner.RunnerStep
import io.gaia_app.runner.StepLogger
import org.springframework.boot.test.context.SpringBootTest
import io.gaia_app.runner.k8s.K8SExecutor
import io.gaia_app.runner.k8s.K8SClientConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.test.context.TestPropertySource
import org.springframework.beans.factory.annotation.Autowired
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

@SpringBootTest(classes = [K8SExecutor::class, K8SClientConfig::class, K8SConfigurationProperties::class])
@EnableConfigurationProperties
@TestPropertySource(properties = [
    "gaia.runner.executor=k8s",
    "gaia.runner.k8s.namespace=gaia-runner",
])
class K8SExecutorIT {

    @Autowired
    lateinit var k8sExecutor: K8SExecutor

    private val image = "hashicorp/terraform:0.13.0"

    private val printlnLogger = StepLogger { }

    @Test
    fun `runContainerForJob() should work with a simple script`() {
        val script = "echo 'Hello World'; exit 0;"
        val step = RunnerStep(UUID.randomUUID().toString(), image, script, listOf())

        Assertions.assertEquals(0, k8sExecutor.executeJobStep(step, printlnLogger).toLong())
    }

    @Test
    fun `runContainerForJob() should stop work with a simple script`() {
        val script = "set -e; echo 'Hello World'; false; exit 0;"
        val step = RunnerStep(UUID.randomUUID().toString(), image, script, listOf())

        Assertions.assertEquals(1, k8sExecutor.executeJobStep(step, printlnLogger).toLong())
    }

    @Test
    fun `runContainerForJob() should return the script exit code`() {
        val script = "exit 5"
        val step = RunnerStep(UUID.randomUUID().toString(), image, script, listOf())

        Assertions.assertEquals(5, k8sExecutor.executeJobStep(step, printlnLogger).toLong())
    }

    @Test
    fun `runContainerForJob() should feed step with container logs`() {
        val script = "echo 'hello world'; echo 'hello again'; exit 0;"
        val step = RunnerStep(UUID.randomUUID().toString(), image, script, listOf())

        val logs = mutableListOf<String>()
        val listLogger = StepLogger { logs.add(it) }

        k8sExecutor.executeJobStep(step, listLogger)
        org.assertj.core.api.Assertions.assertThat(logs).isEqualTo(listOf("hello world\n","hello again\n"))
    }

    @Test
    fun `runContainerForJob() use env of the job`() {
        val script = "echo \$AWS_ACCESS_KEY_ID; exit 0;"
        val step = RunnerStep(UUID.randomUUID().toString(), image, script, listOf("AWS_ACCESS_KEY_ID=SOME_ACCESS_KEY"))

        val logs = mutableListOf<String>()
        val listLogger = StepLogger { logs.add(it) }

        k8sExecutor.executeJobStep(step, listLogger)

        org.assertj.core.api.Assertions.assertThat(logs).isEqualTo(listOf("SOME_ACCESS_KEY\n"))
    }

    @Test
    fun `runContainerForJob() use TF_IN_AUTOMATION env var`() {
        val script = "echo \$TF_IN_AUTOMATION; exit 0;"
        val step = RunnerStep(UUID.randomUUID().toString(), image, script, listOf())

        val logs = mutableListOf<String>()
        val listLogger = StepLogger { logs.add(it) }

        k8sExecutor.executeJobStep(step, listLogger)

        org.assertj.core.api.Assertions.assertThat(logs).isEqualTo(listOf("true\n"));
    }
}