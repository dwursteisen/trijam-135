package your.game

import com.github.dwursteisen.minigdx.GameContext
import com.github.dwursteisen.minigdx.Seconds
import com.github.dwursteisen.minigdx.ecs.Engine
import com.github.dwursteisen.minigdx.ecs.components.Component
import com.github.dwursteisen.minigdx.ecs.entities.Entity
import com.github.dwursteisen.minigdx.ecs.entities.EntityFactory
import com.github.dwursteisen.minigdx.ecs.entities.position
import com.github.dwursteisen.minigdx.ecs.events.Event
import com.github.dwursteisen.minigdx.ecs.physics.AABBCollisionResolver
import com.github.dwursteisen.minigdx.ecs.systems.EntityQuery
import com.github.dwursteisen.minigdx.ecs.systems.System
import com.github.dwursteisen.minigdx.ecs.systems.TemporalSystem
import com.github.dwursteisen.minigdx.file.get
import com.github.dwursteisen.minigdx.game.Game
import com.github.dwursteisen.minigdx.graph.GraphScene
import com.github.dwursteisen.minigdx.input.Key
import com.github.dwursteisen.minigdx.math.ImmutableVector3
import com.github.dwursteisen.minigdx.math.Interpolations
import kotlin.random.Random

class Player : Component
class Circle(var touched: Boolean = false) : Component
class Square(var touched: Boolean = false) : Component
class Movable : Component

class Generator : Component
class Hit : Component
class Destroy : Component

class CreateCircle(var position: ImmutableVector3) : Event
class CreateSquare(var position: ImmutableVector3) : Event

object HitCircle : Event
object HitSquare : Event
class StopGame(score: Int) : Event

class ScoreSystem : System(EntityQuery.none()) {

    var score = 0

    override fun update(delta: Seconds, entity: Entity) = Unit

    override fun onEvent(event: Event, entityQuery: EntityQuery?) {
        if (event is HitSquare) {
            println("Stop")
            emit(StopGame(score))
            score = 0
        } else if (event is HitCircle) {
            score += 10
            println("Score: $score")
        }
    }
}

class HitSystem : System(EntityQuery.of(Hit::class)) {

    val players by interested(EntityQuery.of(Player::class))
    val circles by interested(EntityQuery.of(Circle::class))
    val square by interested(EntityQuery.of(Square::class))

    private val collider = AABBCollisionResolver()

    override fun update(delta: Seconds, entity: Entity) {

        if (collider.collide(entity, players.first())) {
            val firstOrNull =
                circles.filter { !it.get(Circle::class).touched }.firstOrNull { collider.collide(entity, it) }
            if (firstOrNull != null) {
                firstOrNull.get(Circle::class).touched = true
                emit(HitCircle)
            } else {
                val any = square.filter { !it.get(Square::class).touched }.firstOrNull { collider.collide(entity, it) }
                if (any != null) {
                    any.get(Square::class).touched = true
                    emit(HitSquare)
                }
            }
        }
    }
}

class GeneratorSystem() : TemporalSystem(0.3f, query = EntityQuery.none()) {

    val generators by interested(EntityQuery.of(Generator::class))

    override fun update(delta: Seconds, entity: Entity) = Unit

    override fun timeElapsed() {
        val generator = generators.random()
        val randomValue = Random.nextFloat()

        if (randomValue < 0.5f) {
            emit(CreateCircle(generator.position.translation))
        } else if (randomValue < 0.75) {
            emit(CreateSquare(generator.position.translation))
        }
    }
}

class CircleSystem : System(EntityQuery.of(Movable::class)) {

    val destroyers by interested(EntityQuery.of(Destroy::class))

    var limit: Float = 0f

    override fun onGameStarted(engine: Engine) {
        limit = destroyers.first().position.translation.y
    }

    override fun update(delta: Seconds, entity: Entity) {
        entity.position.addLocalTranslation(y = -4f, delta = delta)

        // Out of the screen
        if (entity.position.translation.y <= limit) {
            entity.destroy()
        }
    }

    override fun onEvent(event: Event, entityQuery: EntityQuery?) {
        if (event is CreateCircle) {
            val entity = entityFactory.createFromTemplate("circle")
            entity.position.setLocalTranslation(
                event.position.x,
                event.position.y,
                event.position.z
            )
        } else if (event is CreateSquare) {
            val entity = entityFactory.createFromTemplate("cube")
            entity.position.setLocalTranslation(
                event.position.x,
                event.position.y,
                event.position.z
            )
        }
    }
}

class PlayerSystem : System(EntityQuery.of(Player::class)) {

    lateinit var left: Entity
    lateinit var right: Entity

    val hits by interested(EntityQuery.of(Hit::class))

    lateinit var current: Entity

    override fun onGameStarted(engine: Engine) {
        val (a, b) = hits
        if (a.name == "hit2") {
            left = a
            right = b
        } else {
            right = a
            left = b
        }

        current = left
    }

    override fun update(delta: Seconds, entity: Entity) {
        if (input.isKeyJustPressed(Key.ARROW_LEFT)) {
            current = left
        } else if (input.isKeyJustPressed(Key.ARROW_RIGHT)) {
            current = right
        }
        entity.position.setLocalTranslation(
            x = Interpolations.lerp(
                current.position.translation.x,
                entity.position.translation.x,
                step = 0.8f
            )
        )
    }
}

class MyGame(override val gameContext: GameContext) : Game {

    private val scene by gameContext.fileHandler.get<GraphScene>("circles.protobuf")

    override fun createEntities(entityFactory: EntityFactory) {
        // Create all entities needed at startup
        // The scene is the node graph that can be updated in Blender
        scene.nodes.forEach { node ->
            // Create an entity using all information from this node (model, position, camera, ...)
            if (node.name.startsWith("hit")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Hit())
            } else if (node.name.startsWith("player")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Player())
            } else if (node.name.startsWith("destroy")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Destroy())
            } else if (node.name.startsWith("circle")) {
                entityFactory.registerTemplate("circle") {
                    val entity = entityFactory.createFromNode(node)
                    entity.add(Circle())
                    entity.add(Movable())
                    entity
                }
            } else if (node.name.startsWith("generator")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Generator())
            } else if (node.name.startsWith("cube")) {
                entityFactory.registerTemplate("cube") {
                    val entity = entityFactory.createFromNode(node)
                    entity.add(Square())
                    entity.add(Movable())
                    entity
                }
            } else {
                entityFactory.createFromNode(node)
            }
        }
    }

    override fun createSystems(engine: Engine): List<System> {
        // Create all systems used by the game
        return listOf(
            PlayerSystem(),
            CircleSystem(),
            GeneratorSystem(),
            HitSystem(),
            ScoreSystem()
        )
    }
}
