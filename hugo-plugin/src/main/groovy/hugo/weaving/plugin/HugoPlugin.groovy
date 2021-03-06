package hugo.weaving.plugin

import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class HugoPlugin implements Plugin<Project> {
  def VERSION_NAME = "2.0.0"
  @Override void apply(Project project) {
    def hasApp = project.plugins.findPlugin("com.android.application")

    def hasLib = project.plugins.findPlugin("com.android.library")
    if (!hasApp && !hasLib) {
      throw new IllegalStateException("'android' or 'android-library' plugin required.")
    }

    final def log = project.logger
    final def variants
    if (hasApp) {
      variants = project.android.applicationVariants
    } else {
      variants = project.android.libraryVariants
    }

    project.dependencies {
      debugImplementation "com.github.fangzhzh.hugo:hugo-runtime:${VERSION_NAME}"
      // TODO this should come transitively
      debugImplementation 'org.aspectj:aspectjrt:1.9.1'
      implementation "com.github.fangzhzh.hugo:hugo-annotations:${VERSION_NAME}"
    }

    project.extensions.create('hugo', HugoExtension)

    variants.all { variant ->
      if (!variant.buildType.isDebuggable()) {
        log.debug("Skipping non-debuggable build type '${variant.buildType.name}'.")
        return;
      } else if (!project.hugo.enabled) {
        log.debug("Hugo is not disabled.")
        return;
      }

      Task javaCompile
      if (variant.hasProperty('javaCompileProvider')) {
        // Android 3.3.0+
        javaCompile = variant.javaCompileProvider.get()
      } else {
        javaCompile = variant.javaCompile
      }
      javaCompile.doLast {
        String[] args = [
            "-showWeaveInfo",
            "-1.5",
            "-inpath", javaCompile.destinationDir.toString(),
            "-aspectpath", javaCompile.classpath.asPath,
            "-d", javaCompile.destinationDir.toString(),
            "-classpath", javaCompile.classpath.asPath,
            "-bootclasspath", project.android.bootClasspath.join(File.pathSeparator)
        ]
        log.debug "ajc args: " + Arrays.toString(args)

        MessageHandler handler = new MessageHandler(true);
        new Main().run(args, handler);
        for (IMessage message : handler.getMessages(null, true)) {
          switch (message.getKind()) {
            case IMessage.ABORT:
            case IMessage.ERROR:
            case IMessage.FAIL:
              log.error message.message, message.thrown
              break;
            case IMessage.WARNING:
              log.warn message.message, message.thrown
              break;
            case IMessage.INFO:
              log.info message.message, message.thrown
              break;
            case IMessage.DEBUG:
              log.debug message.message, message.thrown
              break;
          }
        }
      }
    }
  }
}
