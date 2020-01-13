package hugo.weaving.plugin

import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

class HugoPlugin implements Plugin<Project> {
  @Override void apply(Project project) {
//    def hasApp = project.plugins.withType(AppPlugin)
    print(project.plugins)
//    def hasLib = project.plugins.withType(LibraryPlugin)
//    if (!hasApp && !hasLib) {
//      throw new IllegalStateException("'android' or 'android-library' plugin required.")
//    }

    final def log = project.logger
    final def variants
//    if (hasApp) {
//      variants = project.android.applicationVariants
//    } else {
//      variants = project.android.libraryVariants
//    }
    variants = project.android.applicationVariants

    project.dependencies {
      debugImplementation 'com.jakewharton.hugo:hugo-runtime:1.2.1'
      // TODO this should come transitively
      debugImplementation 'org.aspectj:aspectjrt:1.8.6'
      implementation 'com.jakewharton.hugo:hugo-annotations:1.2.2'
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
