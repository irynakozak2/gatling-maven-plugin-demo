package computerdatabase

import java.text.SimpleDateFormat
import java.util.Date

import scala.util.Random
import io.gatling.core
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import io.gatling.core.feeder._
import io.gatling.core.structure.ChainBuilder

class BasicSimulation extends Simulation {


  val stringCharsSeq = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
  val intCharsSeq = "123456789"

  def getRandomString(n:Int) = (1 to n).map(_ => stringCharsSeq(Random.nextInt(stringCharsSeq.length))).mkString
  def getRandomInt(n:Int) = (1 to n).map(_ => intCharsSeq(Random.nextInt(intCharsSeq.length))).mkString
  def timeFormatForES() = {
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date).toString
  }

  val createESLogTest = exec(http("Create ES Log")
    .post("http://35.180.22.52:32510/index_logs")
    .body(StringBody(session => session("generate_eslogs_batch_json").as[String])).asJson
  )

  def generateJsonForBatchESLogs: ChainBuilder = {
    exec(session => {
      var jsonBody: String = """{"logs": ["""
      for(i <- 1 until 10000) {
        val x_variable = "log_message" + i
        val tmpLog = session(s"${x_variable}").as[String]
        jsonBody ++= s"""{\"uuid\": \"${getRandomString(36)}\", \"log_time\": \"${timeFormatForES()}\", \"log_message\": \"${tmpLog}\", \"item_id\": ${getRandomInt(5)}, \"launch_id\": ${getRandomInt(5)}, \"last_modified\": \"${timeFormatForES()}\", \"log_level\": ${getRandomInt(5)}, \"attachment_id\": ${getRandomInt(5)}}"""
        if(i + 1 != 10000){
          jsonBody ++= ","
        }
      }
      jsonBody ++= """], "project": 9575}"""
      session.set("generate_eslogs_batch_json", jsonBody)
    })
  }

  val logMessage = csv("log_messages_50testrows.csv").batch(2000).queue
  val logsCount = logMessage.readRecords.size

  val httpProtocol = http
    .baseUrl("http://computer-database.gatling.io") // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val scn = scenario("Scenario Name")
    .during(1800.seconds, exitASAP = false) {
      feed(logMessage, 10000)
        .exec(generateJsonForBatchESLogs)
        .exec(createESLogTest)
    }

  setUp(scn.inject(rampUsers(1).during(1800.seconds)).protocols(httpProtocol))
}