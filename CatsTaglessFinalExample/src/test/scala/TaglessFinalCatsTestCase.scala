import cats.arrow.FunctionK
import cats.{Comonad, Monad, Semigroup, ~>}
import org.scalatest.FlatSpec
import cats.implicits._
import cats.data.{StateT, Writer, WriterT}
import cats.effect.IO
import cats.tagless.autoFunctorK
import shapeless.tag
import shapeless.tag.@@

class TaglessFinalCatsTestCase extends FlatSpec{
    /**
     * Cats 也提供了 Tagless final 支持(https://typelevel.org/cats-tagless/)
     * */
    "Use Cats-tagless for single Dsl" should "" in {
        /**
         * 用常规方法实现 Tagless Dsl
         * */
        // AuthDsl
        sealed trait EmailAddress
        final case class User(email: String @@ EmailAddress, password: String)

        sealed trait Error
        final case class RegistrationError(message:String) extends Error
        final case class AuthError(message:String) extends Error

        /**
         * 1）声明 `@autoFunctorK`。对于普通的 Tagless Final 则没必要（２a）。`@autoFunctorK` 自动生成 Free 代理和 evidence
         * （参见 Eff 例子的说明），在使用的时候只要　`import AuthDsl.autoDerive._` 即可获得需要的一切。（参考 2b 处的说明。）
         *
         *  为了使用 autoXXX 宏，需要在 build.sbt 中引入:
         *      "org.typelevel" %% "cats-tagless-macros" % Version
         * */
        @autoFunctorK
        trait AuthDsl[F[_]] {
            def register(email: String @@ EmailAddress, password: String): F[Either[RegistrationError, User]]
            def auth(email: String @@ EmailAddress, password: String): F[Either[AuthError, User]]
        }

        object AuthDsl {
            type UserRepository = List[User]
            type UserRepositoryState[A] = StateT[IO, UserRepository, A]
            /**
             * 实现 interpreter，和 Eff 的例子差不多，但是必须声明为隐式。
             * */
            implicit object AuthDslInterpreter extends AuthDsl[UserRepositoryState] {
                override def register(email: String @@ EmailAddress, password: String): UserRepositoryState[Either[RegistrationError, User]] =
                    StateT { users => IO{
                        if (users.exists(_.email === email))
                            (users, RegistrationError("User already exists").asLeft)
                        else {
                            val user = User(email, password)
                            (users :+ user, user.asRight)
                        }
                    } }

                override def auth(email: String @@ EmailAddress, password: String): UserRepositoryState[Either[AuthError, User]] =
                    StateT.inspect(
                        _.find(user => user.email === email && user.password == password)
                                .toRight(AuthError("Authentication failed")))
            }
        }

        /* 服务 */
        object Service {
            def registerAndLogin[F[_]: Monad: AuthDsl]/*(implicit authDsl: AuthDsl[F])*/: F[Either[AuthError, User]] = {
                val email = tag[EmailAddress]("john@doe.com")
                val password = "swordfish"

                val authDsl = implicitly[AuthDsl[F]]
                /**
                 * import cats.implicits._
                 * */
                for {
                    _ <- authDsl.register(email, password)
                    authenticated <- authDsl.auth(email, password)
                } yield authenticated
            }
        }

        /* 使用 */
        import Service._
        import AuthDsl.UserRepositoryState

        /**
         * 2a) 一般来讲，对于一个普通的 Tagless Final，完成了定义就可以直接使用了。(通过 `UserRepositoryState` 告知编译器选择哪个 interpreter)
         * */
        registerAndLogin[UserRepositoryState].run(List.empty[User]).unsafeRunSync().map{
            case Right(user) =>
                assert(user.email === tag[EmailAddress]("john@doe.com"))
            case Left(_) =>
                ???
        }

        /**
         * 2b) 但是对于存在以下需求的 Tagless Final：
         *
         *     需要结合多种不同的 dsl monad。
         *     有堆栈安全的需求。`@autoFunctorK` 还生成 Free[A, ?] 隐式代理。
         *
         * 和 Eff 类似，Cats tagless 也是通过 Dsl 提供一个代理层来实现 Monad 组合，不同的是 Cats 的这个代理层由 `@autoFunctorK`
         * 自动生成，这就是为什么需要在 trait 签名上声明 `@autoFunctorK`。这个宏将自动生成代理和代理需要的 evidence.
         *
         * 对于多个不同的 dsl monad 的需求参考 TaglessFinalMultipleCatsTestCase。本例演示单个 Dsl 的 Free 需求。
         *
         * "Again, the magic here is that Cats-tagless auto derive an Increment[Free[Try, ?]] when there is an
         * implicit Try ~> Free[Try, ?] and a Increment[Try] in scope. This auto derivation can be turned off using
         * an annotation argument: @autoFunctorK(autoDerivation = false)"
         * */
        import AuthDsl.autoDerive._

        /**
         * 还需要申明 Free.liftF。（将它声明为隐式）
         * */
        import cats.free.Free
        implicit def toFree[F[_]]: F ~> Free[F, ?] = λ[F ~> Free[F, ?]](t => Free.liftF(t))

        /**
         * 现在可以进行堆栈安全的调用了。（忽略支持 Free 类型参数的 AutoDsl 隐式实例找不到的警告，autoDerive 将为我们动态生成它）
         * */
        registerAndLogin[Free[UserRepositoryState, ?]].foldMap(FunctionK.id).run(List.empty[User]).unsafeRunSync().map{
            case Right(user) =>
                assert(user.email === tag[EmailAddress]("john@doe.com"))
            case Left(_) =>
                ???
        }
    }


    "Tagless final for Writer without Cats" should "" in {
        /**
         * 与 State 不同，当我们处理 Writer 的时候需要一些额外的步骤：
         * */
        sealed trait EmailAddress
        final case class Email(to: String, subject: String, body: String)

        trait NotifyDsl[F[_]] {
            def send(to: String, subject: String, body: String): F[Unit]
        }

        object EmailDsl {
            type EMailWriter[A] = WriterT[IO, Email, A]
            implicit val notifyInterpreter: NotifyDsl[EMailWriter] = (to: String, subject: String, body: String) =>
                WriterT.tell(Email(to, subject, body))

            /**
             * 与 State 不同，Writer/WriterT 需要自己实现 Monad[Writer]. 原因详见 pure 和 flatMap 的说明。
             * */
            implicit object notifyInterpreterMonad extends Monad[EMailWriter] {
                /**
                 * 与 State 具有从环境中读取环境值不同，Writer 不接受输入的环境值，只是单向地输出新值，这就要求 Monad[Writer]
                 * 具有自己构造环境，并填充初始值的能力。
                 *
                 * Monad 的 pure 是（初始化）构造（新的） Monad 的函数，对于 State 而言，它的传入参数来自 run, 并以此构造出
                 * 环境，因此 Cats 能够帮我们自动生成，但是我们无法将参数通过 run 传递给 Writer（我们无法传入并不等于 Writer
                 * 无法取得，参考 flatMap 的说明），因此 Cats 无法自动生成 Monad[Writer] 而要我们手工实现。
                 * */
                override def pure[A](a: A): EMailWriter[A] = WriterT(IO(Email("empty", "", ""), a))  // 手工生成 Empty 值
                /**
                 * 除了依靠 pure 生成 Empty（环境），我们还要考虑新旧环境值合并的问题，State 的做法将旧的环境值传给用户，然后由用
                 * 户决定如何实现合并。前面说过虽然不能向 Writer 传递环境值（比如 Empty）但是并不等于 Writer 无法处理环境值。如果
                 * 我们查看 WriterT 的 flatMap 我们可以看到它有一个隐式 Semigroup 参数，实际上 Writer（包括 State）视环境值为
                 * Monoid，并且 Writer 利用 Monoid(Semigroup) 的 combination 函数来实现了环境值的合并，（只不过由于 State
                 * 将合并问题留给了用户，因此我们可以通过非 Monoid 标准的方法来合并它们，从而可能造成人为忽略了这个要求）而 Writer
                 * 就需要提供一个隐式 Semigroup 来做这件事了。所以我们还需要提供一个隐式 Semigroup.
                 * */
                override def flatMap[A, B](fa: EMailWriter[A])(f: A => EMailWriter[B]): EMailWriter[B] = fa flatMap f

                override def tailRecM[A, B](a: A)(f: A => EMailWriter[Either[A, B]]): EMailWriter[B] = f(a).map{
                    case Right(b) => b
                }
            }

            /**
             * 实现新旧环境值合并的 combination。详细参考 flatMap 的说明(第一个参数是新的值，第二个是旧的值)，这里我们简单丢弃
             * 旧的邮件地址，但是如果是处理日志，那么可能需要同时保留新旧值。
             * */
            implicit val _semigroup: Semigroup[Email] = (x: Email, _: Email) => x
        }

        object Service {
            def emailNotify[F[_]: Monad](implicit notifyDsl: NotifyDsl[F]): F[Unit] = {
                val email = "john@doe.com"
                for {
                    _ <- notifyDsl.send(email, "Hello", "Thank you for registering")
                } yield ()
            }
        }

        import Service._
        import EmailDsl.EMailWriter
        import EmailDsl._

        /** Writer 并不接受传入参数，因此 run 没有 Empty 环境值。详细说明参考上面的 pure */
        emailNotify[EMailWriter].run.unsafeRunSync()
    }


    "Use Cats-tagless for email Dsl" should "" in {
        /**
         * 即便使用 Cats 也无法帮我们自动解决 Writer 不能传入环境变量的问题，因此也需要我们添加一些额外步骤。但是与不使用Cats不同，
         * Cats 定义了 Comonad（反 Monad）界面来实现相同的功能：
         * */
        sealed trait EmailAddress
        final case class Email(to: String @@ EmailAddress, subject: String, body: String)

        @autoFunctorK
        trait EmailDsl[F[_]] {
            def send(to: String @@ EmailAddress, subject: String, body: String): F[Unit]
        }

        object EmailDsl {
            /**
             * 假设 Email DSL 的容器是 Writer
             * */
            type EMailWriter[A] = WriterT[IO, Email, A]

            implicit object emailInterpreter extends EmailDsl[EMailWriter] {
                override def send(to: String @@ EmailAddress, subject: String, body: String): EmailDsl.EMailWriter[Unit] =
                    WriterT(IO(Email(to, subject, body),()))
            }

            /**
             * 实现 Comonad 界面。
             *
             * 参考：https://typelevel.org/cats/typeclasses/comonad.html
             * */
            implicit val emailInterpreterComonad = new Comonad[EMailWriter] {
                /**
                 * 与非 Cats Tagless 不同。Cats 会执行客户程序，让 WriterT.tell(Email(to, subject, body)) 生成环境数据，
                 * 因此我们不需要为 pure 烦恼。但是我们需要自己照顾 A。Comonad 设计了 extract 方法来从 Monad 中得到 A。由于
                 * 我们将 A 装入了 IO，因此此时我们需要执行IO，才能执行 A.
                 * */
                override def extract[A](x: EMailWriter[A]): A = x.run.unsafeRunSync()._2
                override def coflatMap[A, B](fa: EMailWriter[A])(f: EMailWriter[A] => B): EMailWriter[B] = fa.coflatMap(f)
                override def map[A, B](fa: EMailWriter[A])(f: A => B): EMailWriter[B] = fa.map(a => f(a))
            }
        }

        /* 服务 */
        object Service {
            import cats.implicits._
            def registerAndLogin[F[_]: Monad: EmailDsl]: F[Unit] = {
                val email = tag[EmailAddress]("john@doe.com")
                for {
                    _ <- implicitly[EmailDsl[F]].send(email, "Hello", "Thank you for registering")
                } yield ()
            }
        }

        /* 使用 */
        import Service._
        import EmailDsl.EMailWriter

        /**
         * 2a) 执行
         * */
        import EmailDsl.autoDerive._
        import EmailDsl.emailInterpreterComonad

        import cats.free.Free
        implicit def toFree[F[_]]: F ~> Free[F, ?] = λ[F ~> Free[F, ?]](t => Free.liftF(t))

        registerAndLogin[Free[EMailWriter, ?]].run
    }

}
