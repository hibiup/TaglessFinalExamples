import org.scalatest.FlatSpec

class TaggedTypeTestCase extends FlatSpec{
    /**
     * 参考: http://www.vlachjosef.com/tagged-types-introduction/
     *
     * 假设一个在线 RPG 游戏, 游戏允许玩家 P2P 对战. 这个游戏中存在多个角斗场（arena），一对玩家被放置于笼子（bracket）中
     * 决斗，直至一番胜出，
     * */
    "Tagged type" should "" in {
        object Solution1 {
            /**
             * 1) 定义 ADT
             **/
            // 竞技场
            case class Arena(
                                    id: String, // unique identifier of the arena
                                    name: String // name of the arena
                            )

            // 笼子
            case class Bracket(
                                      id: String, // unique identifier of pvp bracket
                                      arenaId: String // 属于哪个竞技场
                              )

            // 玩家
            case class PlayerProfile(
                                            id: String, // unique player profile identifier
                                            bracketMapping: Map[String, String] // 被装进哪个竞技场的哪个笼子（arena -> bracket）。允许一人同时进入多个笼子
                                    ) {
                /**
                 * Change current bracket of player in arena
                 */
                def changeBracket(arena: Arena, bracket: Bracket): PlayerProfile = {
                    this.copy(bracketMapping = this.bracketMapping + (arena.id -> bracket.id))
                }
            }

            def apply() = {
                /**
                 * 2) 新建若干个竞技场, 笼子和玩家：
                 **/
                // 两个竞技场： firePit， iceDungeon
                val firePit = Arena("firePit", "Fire Pit")
                val iceDungeon = Arena("iceDungeon", "Ice Dungeon")

                // 三个笼子分属两个竞技场
                val bracket1 = Bracket("bracket1", "firePit")
                val bracket2 = Bracket("bracket2", "firePit")
                val bracket3 = Bracket("bracket3", "iceDungeon")

                // 三个玩家:
                /**
                 * 当我们在为 bracketMapping 赋值的时候会发现 `Map[String, String]` 它仅仅反映了数据类型 String -> String 的映射关系，
                 * 不能明确地反映出 Arena -> Bracket 之间的业务含义。这随着代码越来越复杂，可读性将会降低。** 而且更严重的是，如果
                 * 我们不小心互换了 Arena 和 Bracket 的值，编译器并不能发现这个错误，因为两者的数据类型是一样的。**
                 */
                val player1 = PlayerProfile("player1", Map(firePit.id -> bracket1.id, iceDungeon.id -> bracket3.id))
                val player2 = PlayerProfile("player2", Map(firePit.id -> bracket1.id))
                val player3 = PlayerProfile("player3", Map(firePit.id -> bracket2.id, iceDungeon.id -> bracket3.id))
            }

        }

        object Solution2{
            /**
             * Solution2 通过 Shapeless 的 Tagged type 解决了以上问题：
             * */

            /**
             * 1) 定义一些 `Tag`。Tag 就是一些没有任何参数和实现的 trait
             * */
            trait ProfileIdTag
            trait ArenaIdTag
            trait BracketIdTag

            /**
             * 2) 通过 `@@` 类型给相同的 String 打上不同的标签(tag)，从而得到不同的定义，这些新的类型就是 `Tagged type`
             * */
            import shapeless.tag.@@
            type ProfileId = String @@ ProfileIdTag
            type ArenaId   = String @@ ArenaIdTag
            type BracketId = String @@ BracketIdTag

            /**
             * 3) 将这些 Tagged type 用在不同的地方，在使用上并没有什么不同。但是它会让我们避免以上问题。
             * */
            case class Arena(
                                    id: ArenaId, // <- tagged type
                                    name: String
                            )

            case class Bracket(
                                      id: BracketId, // <- tagged type
                                      arenaId: ArenaId // <- tagged type
                              )

            case class PlayerProfile(
                                            id: ProfileId, // <- tagged type
                                            bracketMapping: Map[ArenaId, BracketId] // <- tagged types
                                    ) {
                def changeBracket(arena: Arena, bracket: Bracket): PlayerProfile = {
                    this.copy(bracketMapping = this.bracketMapping + (arena.id -> bracket.id))
                }
            }

            def apply(): Unit = {
                /**
                 * 以下将会错误，因为 Tagged type 阻止了我们用带有歧义的方式为变量赋值。
                 * */
                //PlayerProfile("John", Map("firePit" -> "bracket1"))  // 编译器不能确定 "playerId"， "firePit" 等是合法的值

                /**
                 * 正确的做法是通过 `tag` 函数将 String 值申明成相应的 Tag 变量然后使用。
                 * */
                import shapeless.tag
                val player1Name: ProfileId = tag[ProfileIdTag][String]("John")
                val firstPitId: ArenaId     = tag[ArenaIdTag][String]("firstPit")
                val bracket1Id: BracketId = tag[BracketIdTag][String]("bracket1")

                PlayerProfile(player1Name, Map(firstPitId -> bracket1Id))

                val firePit = Arena(firstPitId, "Fire Pit")
                val bracket1 = Bracket(bracket1Id, firePit.id)
                val player1 = PlayerProfile(player1Name, Map(firePit.id -> bracket1.id))
            }
        }

        Solution2()
    }
}
