import sbt._
import org.ensime.Imports.EnsimeKeys
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
object EnsimeProjectSettings extends AutoPlugin {
  override def requires = org.ensime.EnsimePlugin
  override def trigger = allRequirements
  override def projectSettings = Seq(
    EnsimeKeys.scalariform := ScalariformKeys.preferences.value
  )
}
