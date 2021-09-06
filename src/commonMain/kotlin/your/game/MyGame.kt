package your.game

import com.github.dwursteisen.minigdx.GameContext
import com.github.dwursteisen.minigdx.Seconds
import com.github.dwursteisen.minigdx.ecs.Engine
import com.github.dwursteisen.minigdx.ecs.components.Component
import com.github.dwursteisen.minigdx.ecs.entities.Entity
import com.github.dwursteisen.minigdx.ecs.entities.EntityFactory
import com.github.dwursteisen.minigdx.ecs.entities.position
import com.github.dwursteisen.minigdx.ecs.events.Event
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
class Circle : Component
class Cube : Component

class Generator : Component
class Hit : Component
class Destroy : Component

class CreateCircle(var position: ImmutableVector3) : Event
class CreateCube(var position: ImmutableVector3) : Event

class GeneratorSystem() : TemporalSystem(0.5f, query = EntityQuery.none()) {

    val generators by interested(EntityQuery.of(Generator::class))

    override fun update(delta: Seconds, entity: Entity) = Unit

    override fun timeElapsed() {
        val generator = generators.random()
        val randomValue = Random.nextFloat()

        if (randomValue < 0.5f) {
            emit(CreateCircle(generator.position.translation))
        } else if (randomValue < 0.75) {
            emit(CreateCube(generator.position.translation))
        }
    }
}

class CircleSystem : System(EntityQuery.of(Circle::class)) {

    val destroyers by interested(EntityQuery.of(Destroy::class))

    var limit: Float = 0f

    override fun onGameStarted(engine: Engine) {
        limit = destroyers.first().position.translation.y
    }

    override fun update(delta: Seconds, entity: Entity) {
        entity.position.addLocalTranslation(y = -2f, delta = delta)

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
        } else if (event is CreateCube) {
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
                    entity
                }
            } else if (node.name.startsWith("generator")) {
                val entity = entityFactory.createFromNode(node)
                entity.add(Generator())
            } else if (node.name.startsWith("cube")) {
                entityFactory.registerTemplate("cube") {
                    val entity = entityFactory.createFromNode(node)
                    entity.add(Cube())
                    entity.add(Circle())
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
            GeneratorSystem()
        )
    }
}
