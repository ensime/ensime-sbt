# ENSIME-sbt-cmd 
An sbt plugin that supports integration with the ENSIME IDE.


## Versions

__For use with ensime 0.9+

0.1.0


## How to Install
Add the following to your `~/.sbt/plugins/plugins.sbt` or YOUR_PROJECT/project/plugins.sbt:

    addSbtPlugin("org.ensime" % "ensime-sbt-cmd" % "VERSION")

Adding the line above to YOUR_PROJECT/build.sbt won't activate the plugin, you must add it one level above, to either YOUR_PROJECT/project/plugins.sbt or YOUR_PROJECT/project/build.sbt.

## How to Use
The above automatically adds the `ensime generate` command to your sbt build. This command will write a .ensime file to your project's root directory.

Note: Currently, it may be necessary to first delete your project/target directories before running 'ensime generate'.

## License
BSD License
