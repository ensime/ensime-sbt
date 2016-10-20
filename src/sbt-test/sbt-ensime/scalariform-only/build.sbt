import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

val a = (project in file("a")).
  settings(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value.setPreference(AlignSingleLineCaseStatements, true))

val b = (project in file("b")).
  settings(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value.setPreference(AlignSingleLineCaseStatements, false)
                                       .setPreference(AlignParameters, true))
