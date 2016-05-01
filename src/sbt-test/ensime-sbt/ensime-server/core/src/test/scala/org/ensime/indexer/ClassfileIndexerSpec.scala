// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.indexer

import akka.event.slf4j.SLF4JLogging
import org.ensime.fixture.IsolatedEnsimeVFSFixture
import org.ensime.util.EnsimeSpec
import org.ensime.vfs._

import scala.collection.immutable.Queue

class ClassfileIndexerSpec extends EnsimeSpec with IsolatedEnsimeVFSFixture {

  val indexer = new ClassfileIndexer with SLF4JLogging {}
  import indexer._

  "ClassfileIndexer" should "support Java 6 class files" in withVFS { implicit vfs =>
    val (clazz, refs) = indexClassfile(vfs.vres("jdk6/Test.class"))
    clazz.name shouldBe ClassName(PackageName(List()), "Test")
    clazz.generics shouldBe None
    clazz.superClass shouldBe Some(ClassName(PackageName(List("java", "lang")), "Object"))
    clazz.interfaces shouldBe List()
    clazz.access shouldBe Default
    clazz.deprecated shouldBe false
    clazz.fields shouldBe Queue()
    clazz.methods shouldBe Queue(
      RawMethod(
        name = MemberName(ClassName(PackageName(List()), "Test"), "main"),
        access = Public,
        descriptor = Descriptor(List(ArrayDescriptor(ClassName(PackageName(List("java", "lang")), "String"))), ClassName(PackageName(List()), "void")),
        generics = None,
        line = Some(4)
      )
    )
    clazz.source shouldBe RawSource(Some("Test.java"), Some(1))

    refs shouldBe Set(
      ClassName(PackageName(List()), "void"),
      ClassName(PackageName(List("java", "lang")), "Object"),
      MemberName(ClassName(PackageName(List("java", "lang")), "System"), "out"),
      ClassName(PackageName(List("java", "lang")), "Object"),
      ClassName(PackageName(List("java", "io")), "PrintStream"),
      MemberName(ClassName(PackageName(List("java", "io")), "PrintStream"), "print"),
      ClassName(PackageName(List("java", "lang")), "String")
    )
  }

  it should "support Java 8 class files" in withVFS { implicit vfs =>
    indexClassfile(vfs.vres("jdk8/Test.class"))
    indexClassfile(vfs.vres("jdk8/MyAnnotation.class"))
    indexClassfile(vfs.vres("jdk8/Test$InnerClassWithCtorParam.class"))
  }

  it should "support typical J2SE classes" in withVFS { implicit vfs =>
    val (clazz, refs) = indexClassfile(vfs.vres("java/lang/String.class"))
    clazz.access shouldBe Public
    clazz.name shouldBe ClassName(PackageName(List("java", "lang")), "String")
  }

  it should "support typical Scala classes" in withVFS { implicit vfs =>
    val (clazz, refs) = indexClassfile(vfs.vres("scala/collection/immutable/List.class"))
    clazz.name shouldBe ClassName(PackageName(List("scala", "collection", "immutable")), "List")
  }

  it should "support typical Scala nested classes " in withVFS { implicit vfs =>
    val (clazz, refs) = indexClassfile(vfs.vres("scala/collection/immutable/List$.class"))
    clazz.name shouldBe ClassName(PackageName(List("scala", "collection", "immutable")), "List$")
  }
}
