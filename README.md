# ENSIME-sbt-cmd 
An sbt plugin that supports integration with the ENSIME IDE.
This forked version support an alternate form of the "ensime generate" command: "ensime generate Foo" will generate for only the project called "Foo", which is currently required for working with the sublime-ensime plugin, hence the need for the forked version.


## Versions

__For use with ensime 0.9+

1.0.1


## How to Install
Add the following to your `~/.sbt/plugins/plugins.sbt` or YOUR_PROJECT/project/plugins.sbt:

    addSbtPlugin("com.jglobal" % "ensime-sbt-cmd" % "1.0.1")

Adding the line above to YOUR_PROJECT/build.sbt won't activate the plugin, you must add it one level above, to either YOUR_PROJECT/project/plugins.sbt or YOUR_PROJECT/project/build.sbt.

Given this forked version, you must also run ./sbt in the source of the plugin directory and "publish-local", as this version is not in the generally available repositories in compiled form currently.

## How to Use
The above automatically adds the `ensime generate` command to your sbt build. This command will write a .ensime file to your project's root directory.

Note: Currently, it may be necessary to first delete your project/target directories before running 'ensime generate'.

## License
BSD License
