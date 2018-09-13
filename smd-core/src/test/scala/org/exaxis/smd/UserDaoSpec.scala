package org.exaxis.smd

import akka.japi
import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import de.flapdoodle.embed.mongo.distribution.Version
import org.joda.time.DateTime
import scaldi.{Injectable, Injector, Module}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, Suite, WordSpec}
import play.api.libs.json.Json
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

trait EmbeddedMongoDBPerSuite extends MongoEmbedDatabase with BeforeAndAfterAll { this: Suite =>
  private var mongoProps : MongodProps = null

  override def beforeAll() = {
    mongoProps = mongoStart(54321, Version.V3_6_5)
    super.beforeAll()
  }

  override def afterAll() = {
    try super.afterAll()
    finally mongoStop(mongoProps)
  }
}

class UserDaoSpec extends WordSpec with Injectable with MustMatchers with EmbeddedMongoDBPerSuite{
  implicit val injector = new Module {
    implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

    val mongoUri = "mongodb://localhost:54321/test"
    val driver = new MongoDriver
    val parsedUri = MongoConnection.parseURI(mongoUri)
    val connection = parsedUri.map(driver.connection(_))
    val futureConnection = Future.fromTry(connection)

    bind [ExecutionContext] to executionContext
    bind [Future[DefaultDB]] to futureConnection.flatMap(_.database("test"))

  }

  val userService = new UserService

  "User Service" must {

    def bsonObjectId = BSONObjectID.generate.stringify

    def makeUser(id : Option[String]) = User(
      id,
      "First 1",
      "Last 1",
      "Fake User 1",
      Some(18),
      Some("fake.user1@fake.com"),
      None,
      Some(DateTime.now),
      Some(DateTime.now)
    )

    def makeUser2(id: Option[String]) = User(
      id,
      "First 2",
      "Last 2",
      "Fake User 2",
      Some(21),
      Some("fake.user2@fake.com"),
      None,
      Some(DateTime.now),
      Some(DateTime.now)
    )

    def userEqual(user1:User, user2:User): Unit = {
      user1.id.get mustEqual user2.id.get
      user1.firstName mustEqual user2.firstName
      user1.lastName mustEqual user2.lastName
      user1.fullName mustEqual user2.fullName
      user1.age mustEqual user2.age
      user1.email mustEqual user2.email
      user1.avatarUrl mustEqual user2.avatarUrl
    }

    "not find a user that has not been created" in {
      val response = Await.result(userService.findById(Some(bsonObjectId)), Duration.Inf)
      response mustBe None
    }

    "not delete a user that does not exist" in {
      val response = Await.result(userService.remove(Some(bsonObjectId)), Duration.Inf)
      response.isSuccess mustBe true
      response.get mustBe 0
    }

    "not update a user that does not exist" in {
      val updateResult = Await.result(userService.update(makeUser(Some(bsonObjectId))), Duration.Inf)
      updateResult.isSuccess mustBe true
      updateResult.get.n mustBe 0
      updateResult.get.nModified mustBe 0
    }

    "create user that does not exist and then delete the user" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "not create a user that already exists" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val insertResult2 = Await.result(userService.insert(makeUser2(Some(fakeUserId))), Duration.Inf)
      insertResult2.isFailure mustBe true

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
   }

    "get a user that already exists" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findById(Some(fakeUserId)), Duration.Inf)
      getResult.isEmpty mustEqual false
      userEqual(getResult.get, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "get a user that already exists by alternate attributes" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findOne(Map("firstName"->fakeUser.firstName, "lastName"->fakeUser.lastName)), Duration.Inf)
      getResult.isEmpty mustEqual false
      userEqual(getResult.get, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "update a user that already exists" in {
      val fakeUser1 = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser1), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUser1Id = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val fakeUser2 = makeUser2(Some(fakeUser1Id))
      val updateResult = Await.result(userService.update(fakeUser2), Duration.Inf)
      updateResult.isSuccess mustBe true
      updateResult.get.n mustBe 1

      val getResult = Await.result(userService.findById(Some(fakeUser1Id)), Duration.Inf)
      getResult.isEmpty mustEqual false
      userEqual(getResult.get, fakeUser2)

      val deleteResult = Await.result(userService.remove(Some(fakeUser1Id)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a list of users queryable by an integer attribute" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"18")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a list of users queryable by a DateTime attribute" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("created"->fakeUser.created.get.getMillis.toString)), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "not return a list of users queried with one good match and one bad match " in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"17","created"->fakeUser.created.get.getMillis.toString)), Duration.Inf)
      getResult.isEmpty mustEqual true

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a list of users queryable by an Int and a DateTime attribute" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"18","created"->fakeUser.created.get.getMillis.toString)), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
   }

    "return a user queryable by a complete range of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"(17,19)")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a user queryable by a right-end open partial range of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"(17,)")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a user queryable by a left-end open partial range of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"(,19)")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a user queryable by a right-end open partial inclusive range of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"(18,)")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a user queryable by a left-end open partial inclusive range of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"(,18)")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "not return a user queryable by a right-end open partial exclusive range of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"(19,)")), Duration.Inf)
      getResult.isEmpty mustEqual true

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "not return a user queryable by a left-end open partial exclusive range of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"(,17)")), Duration.Inf)
      getResult.isEmpty mustEqual true

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a list of users when searching text fields" in {
      val fakeUser1 = makeUser(None)
      val fakeUser2 = makeUser2(None)

      val insertResult1 = Await.result(userService.insert(fakeUser1), Duration.Inf)
      insertResult1.isSuccess mustBe true
      val uwr1 = insertResult1.get
      uwr1.n mustBe 1

      val fakeUserId1 = uwr1.upserted.head._id.asInstanceOf[BSONObjectID].stringify


      val insertResult2 = Await.result(userService.insert(fakeUser2), Duration.Inf)
      insertResult2.isSuccess mustBe true
      val uwr2 = insertResult2.get
      uwr2.n mustBe 1

      val fakeUserId2 = uwr2.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(Some("Fake"), None, Map()), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 2
      userEqual(getResult.head, makeUser(Some(fakeUserId1)))
      userEqual(getResult.tail.head, makeUser2(Some(fakeUserId2)))

      val deleteResult1 = Await.result(userService.remove(Some(fakeUserId1)), Duration.Inf)
      deleteResult1.isSuccess mustBe true
      deleteResult1.get mustBe 1

      val deleteResult2 = Await.result(userService.remove(Some(fakeUserId2)), Duration.Inf)
      deleteResult2.isSuccess mustBe true
      deleteResult2.get mustBe 1

    }

    "return a user queryable by an array of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"[17,18,19]")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a user queryable by a 1-element array of Ints" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"[18]")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a user queryable by an array of Ints with match in last element" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"[17,18]")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "return a user queryable by an array of Ints with a match in first element" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"[18,19]")), Duration.Inf)
      getResult.isEmpty mustEqual false
      getResult.length mustEqual 1
      userEqual(getResult.head, makeUser(Some(fakeUserId)))

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }

    "not return a user  queryable by an array of Ints with no matching element" in {
      val fakeUser = makeUser(None)

      val insertResult = Await.result(userService.insert(fakeUser), Duration.Inf)
      insertResult.isSuccess mustBe true
      val uwr = insertResult.get
      uwr.n mustBe 1

      val fakeUserId = uwr.upserted.head._id.asInstanceOf[BSONObjectID].stringify

      val getResult = Await.result(userService.findAll(None, None, params=Map("age"->"[17,19,20]")), Duration.Inf)
      getResult.isEmpty mustEqual true

      val deleteResult = Await.result(userService.remove(Some(fakeUserId)), Duration.Inf)
      deleteResult.isSuccess mustBe true
      deleteResult.get mustBe 1
    }
  }

}
