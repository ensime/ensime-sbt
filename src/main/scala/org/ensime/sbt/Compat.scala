/**
*  Copyright (c) 2010, Aemon Cannon
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions are met:
*      * Redistributions of source code must retain the above copyright
*        notice, this list of conditions and the following disclaimer.
*      * Redistributions in binary form must reproduce the above copyright
*        notice, this list of conditions and the following disclaimer in the
*        documentation and/or other materials provided with the distribution.
*      * Neither the name of ENSIME nor the
*        names of its contributors may be used to endorse or promote products
*        derived from this software without specific prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
*  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
*  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
*  DISCLAIMED. IN NO EVENT SHALL Aemon Cannon BE LIABLE FOR ANY
*  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
*  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
*  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
*  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
*  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
*  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.ensime.sbt
import sbt._
import Keys._

object Compat{

  def evaluateTask[T](key: TaskKey[T])(implicit buildStruct:BuildStructure, s:State, projRef:ProjectRef):Option[T] = {
    s.log.info(" Evaluating task: " + key.key + "...")
    EvaluateTask(buildStruct, key, s, projRef) match {
      case Some((s, Value(result))) => Some(result)
      case _ => {
        s.log.error("Task failed: " + key.key)
        None
      }
    }
  }

  def taskFiles(key:TaskKey[Classpath])(implicit buildStruct:BuildStructure, s:State, projRef:ProjectRef):List[String] = {
    val cp = evaluateTask(key) match {
      case Some(deps) => deps
      case _ => List()
    }
    cp.map{ att => (att.data).getAbsolutePath }.toList
  }

  def optSetting[A](key: SettingKey[A])(implicit x:Extracted, s:State) = {
    s.log.info(" Reading setting: " + key.key + "...")
    x.getOpt(key)
  }

  def settingFiles[A](key: SettingKey[Seq[File]])(implicit x:Extracted, s:State):List[String] = {
    optSetting(key).map(fs => fs.map(_.getAbsolutePath).toList).getOrElse(List[String]())
  }

}
