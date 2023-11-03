package zendo.games.zenlib.physics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class QuadTree {

    public static final int MAX_ENTITIES =5;
    public static final int MAX_LEVELS = 10;

    private Rectangle bounds;

    private Array<Collidable> entities;

    private int level;

    private Array<QuadTree> childNodes;

    // Temp variable
    private static Vector2 CENTER;

    public QuadTree(int level, Rectangle bounds){
        this.level = level;
        this.bounds = bounds;

        entities = new Array<Collidable>();
        childNodes = new Array<QuadTree>(true, 4);
        if (CENTER == null){
            CENTER = new Vector2();
        }
    }

    public void clear() {
        entities.clear();
        for (QuadTree currentNode : childNodes){
            if (currentNode != null) {
                currentNode.clear();
            }
        }
        childNodes.clear();
    }

    private void split() {
        float halfWidth = bounds.width/2f;
        float halfHeight = bounds.height/2f;
        float x = bounds.x;
        float y = bounds.y;

        Rectangle nwRect = new Rectangle(x, y+halfHeight, halfWidth, halfHeight);
        childNodes.add(new QuadTree(level +1, nwRect));

        Rectangle neRect = new Rectangle(x + halfWidth, y + halfHeight, halfWidth, halfHeight);
        childNodes.add(new QuadTree(level + 1, neRect));

        Rectangle swRect = new Rectangle(x, y, halfWidth, halfHeight);
        childNodes.add(new QuadTree(level +1, swRect));

        Rectangle seRect = new Rectangle(x + halfWidth, y, halfWidth, halfHeight);
        childNodes.add(new QuadTree(level + 1, seRect));
    }

    private int getIndex(Collidable entity){
        int index = -1;
        CENTER = bounds.getCenter(CENTER);
        // Object fits completely in the top
        boolean topQuadrant = entity.getCollisionBounds().y > CENTER.y;

        // Object Fits completely in the bottom
        boolean bottomQuadrant = entity.getCollisionBounds().y + entity.getCollisionBounds().height < CENTER.y;

        if (entity.getCollisionBounds().x + entity.getCollisionBounds().width < CENTER.x) {
            if (topQuadrant) index = 0;
            else if (bottomQuadrant) index = 2;
        } else if (entity.getCollisionBounds().x > CENTER.x){
            if (topQuadrant) index = 1;
            else if (bottomQuadrant) index = 3;
        }

        return index;
    }

    public void insert(Collidable entity) {
        if (childNodes.size > 0){
            int index = getIndex(entity);
            if (index != -1){
                childNodes.get(index).insert(entity);
                return;
            }
        }
        entities.add(entity);

        if (entities.size > MAX_ENTITIES && level < MAX_LEVELS && childNodes.size == 0) {
            split();
            int i = 0;
            while ( i < entities.size) {
                int index = getIndex(entities.get(i));
                if (index != -1) {
                    Collidable poppedEntity = entities.removeIndex(i);
                    QuadTree nodeToAdd = childNodes.get(index);
                    nodeToAdd.insert(poppedEntity);
                } else {
                    i++;
                }
            }
        }
    }

    public Array<Collidable> retrieve(Array<Collidable> entitiesToReturn, Collidable entityToSearch) {
        if (childNodes.size > 0) {
            int index = getIndex(entityToSearch);
            if (index != -1) {
                childNodes.get(index).retrieve(entitiesToReturn, entityToSearch);
            } else {
                for(QuadTree node : childNodes){
                    node.retrieve(entitiesToReturn, entityToSearch);
                }
            }
        }

        entitiesToReturn.addAll(entities);

        return entitiesToReturn;
    }

    public boolean remove(Collidable entity) {
        if (childNodes.size > 0) {
            int index = getIndex(entity);
            if (index != -1) {
                return childNodes.get(index).remove(entity);
            }
        }
        return entities.removeValue(entity, true);
    }

    public void renderDebug(SpriteBatch batch, NinePatch ninePatch){
        for (QuadTree node : childNodes){
            node.renderDebug(batch, ninePatch);
        }
        float color = entities.size / 10f;
        batch.setColor(color, 0, 0, 1f);
        if (entities.size > 0) {
            ninePatch.draw(batch, bounds.x, bounds.y, bounds.width, bounds.height);
        }

        batch.setColor(Color.WHITE);
    }
}
