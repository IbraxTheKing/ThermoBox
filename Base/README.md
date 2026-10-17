## 📦 Base Module (Deployment)

Le module `Base` est un module de packaging spécialisé pour le déploiement en production.

### Fonctionnalités principales :
- **Regroupement (Shading)** : Il fusionne les modules `ibr-utils`, `Common`, `Persistence` et `Business` dans un seul fichier JAR (`base.jar`).
- **Optimisation** : Permet de réduire le nombre de dépendances à charger par les clients finaux (API ou Web).

### Utilisation :
Ce module n'est pas utilisé directement pour le développement quotidien, mais est essentiel pour la production. Il doit être compilé avec le profil `deploy` :
[INFO] Scanning for projects...
[INFO] Kotlin smart defaults are enabled for ThermoBox
[INFO] Kotlin smart defaults are enabled for ibr-utils
[INFO] Kotlin smart defaults are enabled for Common
[INFO] Kotlin smart defaults are enabled for Persistence
[INFO] Kotlin smart defaults are enabled for Business
[INFO] Kotlin smart defaults are enabled for RestServer
[INFO] Kotlin smart defaults are enabled for base
[INFO] Kotlin smart defaults are enabled for JSF
[INFO] Kotlin smart defaults are enabled for ThermoBox
[INFO] Kotlin smart defaults are enabled for ibr-utils
[INFO] Kotlin smart defaults are enabled for Common
[INFO] Kotlin smart defaults are enabled for Persistence
[INFO] Kotlin smart defaults are enabled for Business
[INFO] Kotlin smart defaults are enabled for RestServer
[INFO] Kotlin smart defaults are enabled for base
[INFO] Kotlin smart defaults are enabled for JSF
[INFO] Kotlin smart defaults are enabled for ThermoBox
[INFO] Kotlin smart defaults are enabled for ibr-utils
[INFO] Kotlin smart defaults are enabled for Common
[INFO] Kotlin smart defaults are enabled for Persistence
[INFO] Kotlin smart defaults are enabled for Business
[INFO] Kotlin smart defaults are enabled for RestServer
[INFO] Kotlin smart defaults are enabled for base
[INFO] Kotlin smart defaults are enabled for JSF
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Build Order:
[INFO] 
[INFO] ThermoBox                                                          [pom]
[INFO] ibr-utils                                                          [jar]
[INFO] Common                                                             [jar]
[INFO] Persistence                                                        [jar]
[INFO] Business                                                           [jar]
[INFO] RestServer                                                         [jar]
[INFO] base                                                               [jar]
[INFO] JSF                                                                [war]
[INFO] 
[INFO] --------------------------< ax.ibr:ThermoBox >--------------------------
[INFO] Building ThermoBox 1.0-SNAPSHOT                                    [1/8]
[INFO]   from pom.xml
[INFO] --------------------------------[ pom ]---------------------------------
[INFO] 
[INFO] --- kotlin:2.4.10:compile (compile) @ ThermoBox ---
[INFO] No sources to compile
[INFO] 
[INFO] --- compiler:3.14.0:compile (compile) @ ThermoBox ---
[INFO] No sources to compile
[INFO] 
[INFO] --- kotlin:2.4.10:test-compile (test-compile) @ ThermoBox ---
[INFO] No sources to compile
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (testCompile) @ ThermoBox ---
[INFO] No sources to compile
[INFO] 
[INFO] --------------------------< ax.ibr:ibr-utils >--------------------------
[INFO] Building ibr-utils 1.0-SNAPSHOT                                    [2/8]
[INFO]   from ibr-utils\pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- kotlin:2.4.10:kapt (default-kapt) @ ibr-utils ---
[ERROR] Internal Kotlin compilation error
java.nio.file.NoSuchFileException: C:\tmp\kotlin-compilation-session-6485691122720597964-is-running
    at sun.nio.fs.WindowsException.translateToIOException (WindowsException.java:85)
    at sun.nio.fs.WindowsException.rethrowAsIOException (WindowsException.java:104)
    at sun.nio.fs.WindowsException.rethrowAsIOException (WindowsException.java:109)
    at sun.nio.fs.WindowsFileSystemProvider.newByteChannel (WindowsFileSystemProvider.java:231)
    at java.nio.file.Files.newByteChannel (Files.java:357)
    at java.nio.file.Files.createFile (Files.java:610)
    at java.nio.file.TempFileHelper.create (TempFileHelper.java:132)
    at java.nio.file.TempFileHelper.createTempFile (TempFileHelper.java:150)
    at java.nio.file.Files.createTempFile (Files.java:844)
    at org.jetbrains.kotlin.daemon.common.ClientUtilsKt.makeAutodeletingFlagFile (ClientUtils.kt:125)
    at org.jetbrains.kotlin.daemon.common.ClientUtilsKt.makeAutodeletingFlagFile$default (ClientUtils.kt:120)
    at org.jetbrains.kotlin.buildtools.internal.DaemonAdaptersKt.createSessionIsAliveFlagFile (daemonAdapters.kt:144)
    at org.jetbrains.kotlin.buildtools.internal.BaseCompilationOperationImpl.compileWithDaemon$lambda$0 (BaseCompilationOperationImpl.kt:153)
    at org.jetbrains.kotlin.buildtools.internal.BaseCompilationOperationImpl.compileWithDaemon$lambda$1 (BaseCompilationOperationImpl.kt:152)
    at java.util.concurrent.ConcurrentHashMap.computeIfAbsent (ConcurrentHashMap.java:1735)
    at org.jetbrains.kotlin.buildtools.internal.BaseCompilationOperationImpl.compileWithDaemon (BaseCompilationOperationImpl.kt:152)
    at org.jetbrains.kotlin.buildtools.internal.BaseCompilationOperationImpl.executeCancellableImpl (BaseCompilationOperationImpl.kt:91)
    at org.jetbrains.kotlin.buildtools.internal.BaseCompilationOperationImpl.executeCancellableImpl (BaseCompilationOperationImpl.kt:55)
    at org.jetbrains.kotlin.buildtools.internal.CancellableBuildOperationImpl.executeImpl (CancellableBuildOperationImpl.kt:58)
    at org.jetbrains.kotlin.buildtools.internal.BuildOperationImpl.execute (BuildOperationImpl.kt:33)
    at org.jetbrains.kotlin.buildtools.internal.KotlinToolchainsImpl$BuildSessionImpl.executeOperation$lambda$1 (KotlinToolchainsImpl.kt:74)
    at org.jetbrains.kotlin.buildtools.internal.KotlinToolchainsImpl$BuildSessionImpl.executeOperation (KotlinToolchainsImpl.kt:78)
    at org.jetbrains.kotlin.maven.K2JVMCompileMojo.execCompiler (K2JVMCompileMojo.java:421)
    at org.jetbrains.kotlin.maven.kapt.KaptJVMCompilerMojo.execCompiler (KaptJVMCompilerMojo.java:174)
    at org.jetbrains.kotlin.maven.kapt.KaptJVMCompilerMojo.execCompiler (KaptJVMCompilerMojo.java:46)
    at org.jetbrains.kotlin.maven.KotlinCompileMojoBase.execute (KotlinCompileMojoBase.java:191)
    at org.apache.maven.plugin.DefaultBuildPluginManager.executeMojo (DefaultBuildPluginManager.java:126)
    at org.apache.maven.lifecycle.internal.MojoExecutor.doExecute2 (MojoExecutor.java:328)
    at org.apache.maven.lifecycle.internal.MojoExecutor.doExecute (MojoExecutor.java:316)
    at org.apache.maven.lifecycle.internal.MojoExecutor.execute (MojoExecutor.java:212)
    at org.apache.maven.lifecycle.internal.MojoExecutor.execute (MojoExecutor.java:174)
    at org.apache.maven.lifecycle.internal.MojoExecutor.access$000 (MojoExecutor.java:75)
    at org.apache.maven.lifecycle.internal.MojoExecutor$1.run (MojoExecutor.java:162)
    at org.apache.maven.plugin.DefaultMojosExecutionStrategy.execute (DefaultMojosExecutionStrategy.java:39)
    at org.apache.maven.lifecycle.internal.MojoExecutor.execute (MojoExecutor.java:159)
    at org.apache.maven.lifecycle.internal.LifecycleModuleBuilder.buildProject (LifecycleModuleBuilder.java:105)
    at org.apache.maven.lifecycle.internal.LifecycleModuleBuilder.buildProject (LifecycleModuleBuilder.java:73)
    at org.apache.maven.lifecycle.internal.builder.singlethreaded.SingleThreadedBuilder.build (SingleThreadedBuilder.java:53)
    at org.apache.maven.lifecycle.internal.LifecycleStarter.execute (LifecycleStarter.java:118)
    at org.apache.maven.DefaultMaven.doExecute (DefaultMaven.java:261)
    at org.apache.maven.DefaultMaven.doExecute (DefaultMaven.java:173)
    at org.apache.maven.DefaultMaven.execute (DefaultMaven.java:101)
    at org.apache.maven.cli.MavenCli.execute (MavenCli.java:906)
    at org.apache.maven.cli.MavenCli.doMain (MavenCli.java:283)
    at org.apache.maven.cli.MavenCli.main (MavenCli.java:206)
    at jdk.internal.reflect.DirectMethodHandleAccessor.invoke (DirectMethodHandleAccessor.java:104)
    at java.lang.reflect.Method.invoke (Method.java:565)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launchEnhanced (Launcher.java:255)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launch (Launcher.java:201)
    at org.codehaus.plexus.classworlds.launcher.Launcher.mainWithExitCode (Launcher.java:361)
    at org.codehaus.plexus.classworlds.launcher.Launcher.main (Launcher.java:314)
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for ThermoBox 1.0-SNAPSHOT:
[INFO] 
[INFO] ThermoBox .......................................... SUCCESS [  0.334 s]
[INFO] ibr-utils .......................................... FAILURE [  1.803 s]
[INFO] Common ............................................. SKIPPED
[INFO] Persistence ........................................ SKIPPED
[INFO] Business ........................................... SKIPPED
[INFO] RestServer ......................................... SKIPPED
[INFO] base ............................................... SKIPPED
[INFO] JSF ................................................ SKIPPED
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  6.350 s
[INFO] Finished at: 2026-10-17T07:46:38+02:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.jetbrains.kotlin:kotlin-maven-plugin:2.4.10:kapt (default-kapt) on project ibr-utils: Compilation failure -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
[ERROR] 
[ERROR] After correcting the problems, you can resume the build with the command
[ERROR]   mvn <args> -rf :ibr-utils
