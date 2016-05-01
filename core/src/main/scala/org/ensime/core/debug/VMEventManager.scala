// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core.debug

import akka.actor.ActorRef
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.event.{ ClassPrepareEvent, VMDisconnectEvent, EventQueue }
import org.slf4j.LoggerFactory

class VMEventManager(val eventQueue: EventQueue, debugManager: ActorRef) {

  val log = LoggerFactory.getLogger("VMEventManager")

  @volatile private var finished = false;
  private val thread = new Thread() {
    override def run(): Unit = {
      while (!finished) {
        try {
          val eventSet = eventQueue.remove()
          val it = eventSet.eventIterator()
          while (it.hasNext) {
            val evt = it.nextEvent()
            evt match {
              case e: VMDisconnectEvent =>
                throw new InterruptedException
              case e: ClassPrepareEvent =>
                debugManager ! DMClassPrepareEvent(e, eventSet)
              case _ =>
            }
            debugManager ! evt
          }
        } catch {
          case t: VMDisconnectedException =>
            debugManager ! t
            finished = true
          case t: InterruptedException =>
            log.info(s"Interrupting thread for VMEventManager")
            finished = true
          case t: Throwable =>
            log.info("Exception during execution", t)
            finished = true
        }
      }

    }

  }
  def start(): Unit = thread.start()
  def stop(): Unit = {
    finished = true;
    thread.interrupt()
  }
}

