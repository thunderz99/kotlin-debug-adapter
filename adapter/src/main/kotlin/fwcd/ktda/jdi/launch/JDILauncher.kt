package fwcd.ktda.jdi.launch

import fwcd.ktda.LOG
import fwcd.ktda.core.launch.DebugLauncher
import fwcd.ktda.core.launch.LaunchConfiguration
import fwcd.ktda.core.launch.AttachConfiguration
import fwcd.ktda.core.Debuggee
import fwcd.ktda.core.DebugContext
import fwcd.ktda.util.KotlinDAException
import fwcd.ktda.jdi.JDIDebuggee
import com.sun.jdi.Bootstrap
import com.sun.jdi.VirtualMachineManager
import com.sun.jdi.connect.Connector
import com.sun.jdi.connect.LaunchingConnector
import com.sun.jdi.connect.AttachingConnector
import java.io.File
import java.nio.file.Path
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class JDILauncher(
	private val attachTimeout: Int = 50,
	private val vmArguments: String? = null,
	private val modulePaths: String? = null,
	private val environmentVariables: Collection<String>? = null
) : DebugLauncher {
	private val vmManager: VirtualMachineManager
		get() = Bootstrap.virtualMachineManager()
	
	override fun launch(config: LaunchConfiguration, context: DebugContext): JDIDebuggee {
		val connector = createLaunchConnector()
		LOG.info("Starting JVM debug session with main class {}", config.mainClass)
		return JDIDebuggee(
			connector.launch(createLaunchArgs(config, connector)) ?: throw KotlinDAException("Could not launch a new VM"),
			sourcesRootOf(config.projectRoot),
			context
		)
	}
	
	override fun attach(config: AttachConfiguration, context: DebugContext): JDIDebuggee {
		val connector = createAttachConnector()
		LOG.info("Attaching JVM debug session on {}:{}", config.hostName, config.port)
		return JDIDebuggee(
			connector.attach(createAttachArgs(config, connector)) ?: throw KotlinDAException("Could not attach the VM"),
			sourcesRootOf(config.projectRoot),
			context
		)
	}
	
	private fun createLaunchArgs(config: LaunchConfiguration, connector: Connector): Map<String, Connector.Argument> = connector.defaultArguments()
		.also { args ->
			args["suspend"]!!.setValue("true")
			args["options"]!!.setValue(formatOptions(config))
			args["main"]!!.setValue(formatMainClass(config))
			args["cwd"]?.setValue(config.projectRoot.toAbsolutePath().toString())
			args["env"]?.setValue(urlEncode(environmentVariables) ?: "")
		}
	
	private fun createAttachArgs(config: AttachConfiguration, connector: Connector): Map<String, Connector.Argument> = connector.defaultArguments()
		.also { args ->
			args["hostname"]!!.setValue(config.hostName)
			args["port"]!!.setValue(config.port.toString())
			args["timeout"]!!.setValue(config.timeout.toString())
		}
	
	private fun createAttachConnector(): AttachingConnector = vmManager.attachingConnectors()
		.firstOrNull()
		?: throw KotlinDAException("Could not find an attaching connector (for a new debuggee VM)")
	
	private fun createLaunchConnector(): LaunchingConnector = vmManager.launchingConnectors()
		.firstOrNull() // TODO: Investigate whether this connector works fine on JDK 10+
		?: throw KotlinDAException("Could not find a launching connector (for a new debuggee VM)")
	
	private fun sourcesRootOf(projectRoot: Path) = projectRoot.resolve("src").resolve("main").resolve("kotlin")
	
	private fun formatOptions(config: LaunchConfiguration): String {
		var options = ""
		vmArguments?.let { options += it }
		modulePaths?.let { options += " --module-path \"$modulePaths\"" }
		options += " -classpath \"${formatClasspath(config)}\""
		return options
	}
	
	private fun formatMainClass(config: LaunchConfiguration): String {
		val mainClasses = config.mainClass.split("/")
		return if ((modulePaths != null) || (mainClasses.size == 2)) {
			// Required for Java 9 compatibility
			"-m ${config.mainClass}"
		} else config.mainClass
	}
	
	private fun formatClasspath(config: LaunchConfiguration): String = config.classpath
		.map { it.toAbsolutePath().toString() }
		.reduce { prev, next -> "$prev${File.pathSeparatorChar}$next" }
		
	private fun urlEncode(arg: Collection<String>?) = arg
		?.map { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
		?.reduce { a, b -> "$a\n$b" }
		
	private fun urlDecode(arg: String?) = arg
		?.split("\n")
		?.map { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
		?.toList()
}
