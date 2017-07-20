package gaia.cu9.ari.gaiaorbit.util.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeSet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

import gaia.cu9.ari.gaiaorbit.data.stars.OctreeMultiFileLoader;
import gaia.cu9.ari.gaiaorbit.render.ComponentType;
import gaia.cu9.ari.gaiaorbit.render.ILineRenderable;
import gaia.cu9.ari.gaiaorbit.render.system.AbstractRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.LineRenderSystem;
import gaia.cu9.ari.gaiaorbit.scenegraph.AbstractPositionEntity;
import gaia.cu9.ari.gaiaorbit.scenegraph.FovCamera;
import gaia.cu9.ari.gaiaorbit.scenegraph.ICamera;
import gaia.cu9.ari.gaiaorbit.scenegraph.SceneGraphNode;
import gaia.cu9.ari.gaiaorbit.scenegraph.Transform;
import gaia.cu9.ari.gaiaorbit.util.ComponentTypes;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;
import gaia.cu9.ari.gaiaorbit.util.Pair;
import gaia.cu9.ari.gaiaorbit.util.color.ColourUtils;
import gaia.cu9.ari.gaiaorbit.util.math.BoundingBoxd;
import gaia.cu9.ari.gaiaorbit.util.math.Frustumd;
import gaia.cu9.ari.gaiaorbit.util.math.Intersectord;
import gaia.cu9.ari.gaiaorbit.util.math.MathUtilsd;
import gaia.cu9.ari.gaiaorbit.util.math.Rayd;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;

/**
 * Octree node implementation which contains a list of {@link IPosition} objects
 * and possibly 8 subnodes.
 * 
 * @author Toni Sagrista
 */
public class OctreeNode implements ILineRenderable {
    public static int nObserved = 0;
    /** Max depth of the structure this node belongs to **/
    public static int maxDepth;
    /** Is dynamic loading active? **/
    public static boolean LOAD_ACTIVE;

    /**
     * Since OctreeNode is not to be parallelised, these can be static.
     **/
    private static BoundingBoxd boxcopy = new BoundingBoxd(new Vector3d(), new Vector3d());
    private static Vector3d auxD1 = new Vector3d(), auxD3 = new Vector3d(), auxD4 = new Vector3d();
    private static Rayd ray = new Rayd(new Vector3d(), new Vector3d());

    private Vector3d aux3d1;

    /** The load status of this node **/
    private LoadStatus status;
    /** The unique page identifier **/
    public final long pageId;
    /** Contains the bottom-left-front position of the octant **/
    public final Vector3d blf;
    /** Contains the top-right-back position of the octant **/
    public final Vector3d trb;
    /** The centre of this octant **/
    public final Vector3d centre;
    /** The bounding box **/
    public final BoundingBoxd box;
    /** Octant size in x, y and z **/
    public final Vector3d size;
    /** Contains the depth level **/
    public final int depth;
    /** Number of objects contained in this node and its descendants **/
    public int nObjects;
    /** Number of objects contained in this node **/
    public int ownObjects;
    /** Number of children nodes of this node **/
    public int childrenCount;
    /** The parent, if any **/
    public OctreeNode parent;
    /** Children nodes **/
    public OctreeNode[] children = new OctreeNode[8];
    /** List of objects **/
    public Array<AbstractPositionEntity> objects;

    private double radius;
    /** If observed, the view angle in radians of this octant **/
    public double viewAngle;
    /** The distance to the camera in units of the center of this octant **/
    public double distToCamera;
    /** Is this octant observed in this frame? **/
    public boolean observed;
    /** Camera transform to render **/
    Vector3d transform;
    /** The opacity of this node **/
    public float opacity;

    /**
     * Constructs an octree node
     * 
     * @param pageId
     * @param x
     * @param y
     * @param z
     * @param hsx
     * @param hsy
     * @param hsz
     * @param depth
     */
    public OctreeNode(long pageId, double x, double y, double z, double hsx, double hsy, double hsz, int depth) {
        this.pageId = pageId;
        this.blf = new Vector3d(x - hsx, y - hsy, z - hsz);
        this.trb = new Vector3d(x + hsx, y + hsy, z + hsz);
        this.centre = new Vector3d(x, y, z);
        this.size = new Vector3d(hsx * 2, hsy * 2, hsz * 2);
        this.box = new BoundingBoxd(blf, trb);
        this.aux3d1 = new Vector3d();
        this.depth = depth;
        this.transform = new Vector3d();
        this.observed = false;
        this.status = LoadStatus.NOT_LOADED;
        this.radius = Math.sqrt(hsx * hsx + hsy * hsy + hsz * hsz);
    }

    /**
     * Constructs an octree node
     * 
     * @param pageId
     * @param x
     * @param y
     * @param z
     * @param hsx
     * @param hsy
     * @param hsz
     * @param depth
     * @param parent
     *            The parent of this octant
     * @param i
     *            The index in the parent's children
     */
    public OctreeNode(long pageId, double x, double y, double z, double hsx, double hsy, double hsz, int depth, OctreeNode parent, int i) {
        this(pageId, x, y, z, hsx, hsy, hsz, depth);
        this.parent = parent;
        parent.children[i] = this;
    }

    /**
     * Constructs an octree node.
     * 
     * @param pageId
     *            The page id.
     * @param x
     *            The x coordinate of the center.
     * @param y
     *            The y coordinate of the center.
     * @param z
     *            The z coordinate of the center.
     * @param hsx
     *            The half-size in x.
     * @param hsy
     *            The half-size in y.
     * @param hsz
     *            The half-size in z.
     * @param childrenCount
     *            Number of children nodes. Same as non null positions in
     *            children vector.
     * @param nObjects
     *            Number of objects contained in this node and its descendants.
     * @param ownObjects
     *            Number of objects contained in this node. Same as
     *            objects.size().
     */
    public OctreeNode(long pageId, double x, double y, double z, double hsx, double hsy, double hsz, int childrenCount, int nObjects, int ownObjects, int depth) {
        this(pageId, x, y, z, hsx, hsy, hsz, depth);
        this.childrenCount = childrenCount;
        this.nObjects = nObjects;
        this.ownObjects = ownObjects;
    }

    /**
     * Resolves and adds the children of this node using the map. It runs
     * recursively once the children have been added.
     * 
     * @param map
     */
    public void resolveChildren(Map<Long, Pair<OctreeNode, long[]>> map) {
        Pair<OctreeNode, long[]> me = map.get(pageId);
        if (me == null) {
            throw new RuntimeException("OctreeNode with page ID " + pageId + " not found in map");
        }

        long[] childrenIds = me.getSecond();
        int i = 0;
        for (long childId : childrenIds) {
            if (childId >= 0) {
                // Child exists
                OctreeNode child = map.get(childId).getFirst();
                children[i] = child;
                child.parent = this;
            } else {
                // No node in this position
            }
            i++;
        }

        // Recursive running
        for (int j = 0; j < children.length; j++) {
            OctreeNode child = children[j];
            if (child != null) {
                child.resolveChildren(map);
            }
        }
    }

    public boolean add(AbstractPositionEntity e) {
        if (objects == null)
            objects = new Array<AbstractPositionEntity>(false, 100);
        objects.add(e);
        ownObjects = objects.size;
        return true;
    }

    public boolean addAll(Array<AbstractPositionEntity> l) {
        if (objects == null)
            objects = new Array<AbstractPositionEntity>(false, l.size);
        objects.addAll(l);
        ownObjects = objects.size;
        return true;
    }

    public void setObjects(Array<AbstractPositionEntity> l) {
        this.objects = l;
        ownObjects = objects.size;
    }

    public boolean insert(AbstractPositionEntity e, int level) {
        int node = 0;
        if (e.getPosition().y > blf.y + ((trb.y - blf.y) / 2))
            node += 4;
        if (e.getPosition().z > blf.z + ((trb.z - blf.z) / 2))
            node += 2;
        if (e.getPosition().x > blf.x + ((trb.x - blf.x) / 2))
            node += 1;
        if (level == this.depth + 1) {
            return children[node].add(e);
        } else {
            return children[node].insert(e, level);
        }
    }

    public void toTree(TreeSet<AbstractPositionEntity> tree) {
        for (AbstractPositionEntity i : objects) {
            tree.add(i);
        }
        if (children != null) {
            for (int i = 0; i < 8; i++) {
                children[i].toTree(tree);
            }
        }
    }

    /**
     * Adds all the children of this node and its descendants to the given list.
     * 
     * @param tree
     */
    public void addChildrenToList(ArrayList<OctreeNode> tree) {
        if (children != null) {
            for (int i = 0; i < 8; i++) {
                if (children[i] != null) {
                    tree.add(children[i]);
                    children[i].addChildrenToList(tree);
                }
            }
        }
    }

    /**
     * Adds all the particles of this node and its descendants to the given
     * list.
     * 
     * @param particles
     */
    public void addParticlesTo(Array<AbstractPositionEntity> particles) {
        if (this.objects != null) {
            for (AbstractPositionEntity elem : this.objects)
                particles.add(elem);
        }
        for (int i = 0; i < 8; i++) {
            if (children[i] != null) {
                children[i].addParticlesTo(particles);
            }
        }
    }

    public String toString() {
        return toString(false);
    }

    public String toString(boolean rec) {
        StringBuffer str = new StringBuffer(depth);
        if (rec)
            for (int i = 0; i < depth; i++) {
                str.append("   ");
            }

        str.append(pageId).append("(lvl").append(depth).append(")");
        if (parent != null) {
            str.append(" [i: ").append(Arrays.asList(parent.children).indexOf(this)).append(", ownobj: ");
        } else {
            str.append("[ownobj: ");
        }
        str.append(objects != null ? objects.size : "0").append("/").append(ownObjects).append(", recobj: ").append(nObjects).append(", nchld: ").append(childrenCount).append("] ").append(status).append("\n");

        if (childrenCount > 0 && rec) {
            for (OctreeNode child : children) {
                if (child != null) {
                    str.append(child.toString(rec));
                }
            }
        }
        return str.toString();
    }

    public String toStringRec() {
        StringBuffer str = new StringBuffer(depth);
        for (int i = 0; i < depth; i++) {
            str.append("    ");
        }
        str.append(pageId).append("(").append(depth).append(")");
        if (parent != null) {
            str.append(" [i: ").append(Arrays.asList(parent.children).indexOf(this)).append(", ownobj: ");
        } else {
            str.append("[ownobj: ");
        }
        str.append(objects != null ? objects.size : "0").append("/").append(ownObjects).append(", recobj: ").append(nObjects).append(", nchld: ").append(childrenCount).append("] ").append(status).append("\n");
        if (childrenCount > 0) {
            for (OctreeNode child : children) {
                if (child != null) {
                    str.append(child.toString());
                }
            }
        }
        return str.toString();
    }

    /**
     * Counts the number of nodes recursively.
     * 
     * @return
     */
    public int numNodes() {
        int numNodes = 1;
        for (int i = 0; i < 8; i++) {
            if (children[i] != null) {
                numNodes += children[i].numNodes();
            }
        }
        return numNodes;
    }

    ComponentTypes ct = new ComponentTypes(ComponentType.Others);

    @Override
    public ComponentTypes getComponentType() {
        return ct;
    }

    @Override
    public double getDistToCamera() {
        return 0;
    }

    /**
     * Returns the deepest octant that contains the position.
     * 
     * @param position
     * @return
     */
    public OctreeNode getBestOctant(Vector3d position) {
        if (!this.box.contains(position)) {
            return null;
        } else {
            OctreeNode candidate = null;
            for (int i = 0; i < 8; i++) {
                OctreeNode child = children[i];
                if (child != null) {
                    candidate = child.getBestOctant(position);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
            // We could not found a candidate in our children, we use this node.
            return this;
        }
    }

    /**
     * Computes the observed value and the transform of each observed node.
     * 
     * @param parentTransform
     *            The parent transform.
     * @param cam
     *            The current camera.
     * @param roulette
     *            List where the nodes to be processed are to be added.
     * @param opacity
     *            The opacity to set.
     * @return Whether new objects have been added since last frame
     */
    public void update(Transform parentTransform, ICamera cam, Array<SceneGraphNode> roulette, float opacity) {
        parentTransform.getTranslation(transform);
        this.opacity = opacity;

        // Is this octant observed??
        if (!cam.getMode().isGaiaFov()) {
            // Only one view direction
            // computeObserved2(parentTransform, cam.getAngleEdge(),
            // cam.getPos(), cam.getDirection(), cam.getUp());
            computeObserved1(parentTransform, cam.getFrustum());
        } else {
            // FOV, we have two view directions
            // computeObserved2(parentTransform, cam.getAngleEdge(),
            // cam.getPos(), cam.getDirections()[0], cam.getUp());
            computeObserved1(parentTransform, cam.getFrustum());
            if (!observed) {
                // computeObserved2(parentTransform, cam.getAngleEdge(),
                // cam.getPos(), cam.getDirections()[1], cam.getUp());
                computeObserved1(parentTransform, ((FovCamera) (cam.getCurrent())).getFrustum2());
            }
        }

        if (observed) {
            nObserved++;
            /**
             * Load lists of pages
             */
            if (status == LoadStatus.NOT_LOADED && LOAD_ACTIVE) {
                OctreeMultiFileLoader.addToQueue(this);
            } else if (status == LoadStatus.LOADED) {
                OctreeMultiFileLoader.touch(this);
            }

            // Compute distance and view angle
            distToCamera = auxD1.set(centre).add(cam.getInversePos()).len();
            viewAngle = (radius / distToCamera) / cam.getFovFactor();

            if (viewAngle < GlobalConf.scene.OCTANT_THRESHOLD_0 / cam.getFovFactor()) {
                // Stay in current level
                addObjectsTo(roulette);
                setChildrenObserved(false);
            } else {
                // Break down tree, fade in until th2
                double alpha = 1;
                if (GlobalConf.scene.OCTREE_PARTICLE_FADE) {
                    AbstractRenderSystem.POINT_UPDATE_FLAG = true;
                    alpha = MathUtilsd.clamp(MathUtilsd.lint(viewAngle, GlobalConf.scene.OCTANT_THRESHOLD_0 / cam.getFovFactor(), GlobalConf.scene.OCTANT_THRESHOLD_1 / cam.getFovFactor(), 0d, 1d), 0f, 1f);
                }

                // Add objects
                addObjectsTo(roulette);
                // Update children
                for (int i = 0; i < 8; i++) {
                    OctreeNode child = children[i];
                    if (child != null) {
                        child.update(parentTransform, cam, roulette, (float) alpha);
                    }
                }

            }
        }
    }

    private void addObjectsTo(Array<SceneGraphNode> roulette) {
        if (objects != null) {
            roulette.addAll(objects);
        }
    }

    private void setChildrenObserved(boolean observed) {
        for (int i = 0; i < 8; i++) {
            OctreeNode child = children[i];
            if (child != null) {
                child.observed = observed;
            }
        }
    }

    public boolean isObserved() {
        return observed && (parent == null ? true : parent.isObserved());
    }

    /**
     * Checks whether the given frustum intersects with the current octant.
     * 
     * @param parentTransform
     * @param cam
     */
    private boolean computeObserved1(Transform parentTransform, Frustumd frustum) {
        boxcopy.set(box);
        // boxcopy.mul(boxtransf.idt().translate(parentTransform.getTranslation()));

        observed = GlobalConf.program.CUBEMAP360_MODE || frustum.pointInFrustum(boxcopy.getCenter(auxD1)) || frustum.pointInFrustum(boxcopy.getCorner000(auxD1)) || frustum.pointInFrustum(boxcopy.getCorner001(auxD1)) || frustum.pointInFrustum(boxcopy.getCorner010(auxD1)) || frustum.pointInFrustum(boxcopy.getCorner011(auxD1)) || frustum.pointInFrustum(boxcopy.getCorner100(auxD1)) || frustum.pointInFrustum(boxcopy.getCorner101(auxD1)) || frustum.pointInFrustum(boxcopy.getCorner110(auxD1))
                || frustum.pointInFrustum(boxcopy.getCorner111(auxD1));

        for (int i = 0; i < 4; i++) {
            if (!observed) {
                // 0-4
                ray.origin.set(frustum.planePoints[i]);
                ray.direction.set(frustum.planePoints[i + 4]).sub(ray.origin);
                observed = Intersectord.intersectRayBoundsFast(ray, boxcopy.getCenter(auxD3), boxcopy.getDimensions(auxD4));
            } else {
                break;
            }
        }

        return observed;
    }

    /**
     * The octant is observed if at least one of its vertices is in the view or
     * the camera itself is in the view.
     * 
     * @param parentTransform
     * @param angle
     *            Angle edge of camera
     * @param pos
     *            Position of camera
     * @param dir
     *            Direction of camera
     * @param up
     *            Up vector of camera
     */
    // private boolean computeObserved2(Transform parentTransform, float angle,
    // Vector3d pos, Vector3d dir, Vector3d up) {
    // boxcopy.set(box);
    // boxcopy.mul(boxtransf.idt().translate(parentTransform.getTranslation()));
    //
    // observed = GlobalConf.program.CUBEMAP360_MODE ||
    // GlobalResources.isInView(boxcopy.getCenter(auxD1), angle, dir) ||
    // GlobalResources.isInView(boxcopy.getCorner000(auxD1), angle, dir) ||
    // GlobalResources.isInView(boxcopy.getCorner001(auxD1), angle, dir) ||
    // GlobalResources.isInView(boxcopy.getCorner010(auxD1), angle, dir) ||
    // GlobalResources.isInView(boxcopy.getCorner011(auxD1), angle, dir) ||
    // GlobalResources.isInView(boxcopy.getCorner100(auxD1), angle, dir)
    // || GlobalResources.isInView(boxcopy.getCorner101(auxD1), angle, dir) ||
    // GlobalResources.isInView(boxcopy.getCorner110(auxD1), angle, dir) ||
    // GlobalResources.isInView(boxcopy.getCorner111(auxD1), angle, dir) ||
    // box.contains(pos);
    //
    // // Rays
    // if (!observed) {
    // auxD2.set(dir).crs(up);
    //
    // // Rays in direction-up plane (vertical plane)
    // ray.direction.set(auxD1.set(dir).rotate(auxD2, angle));
    // observed = observed || Intersectord.intersectRayBoundsFast(ray,
    // boxcopy.getCenter(auxD3), boxcopy.getDimensions(auxD4));
    // ray.direction.set(auxD1.set(dir).rotate(auxD2, -angle));
    // observed = observed || Intersectord.intersectRayBoundsFast(ray,
    // boxcopy.getCenter(auxD3), boxcopy.getDimensions(auxD4));
    //
    // // Rays in direction-crs(direction,up) plane (horizontal plane)
    // ray.direction.set(auxD1.set(dir).rotate(up, angle));
    // observed = observed || Intersectord.intersectRayBoundsFast(ray,
    // boxcopy.getCenter(auxD3), boxcopy.getDimensions(auxD4));
    // ray.direction.set(auxD1.set(dir).rotate(up, -angle));
    // observed = observed || Intersectord.intersectRayBoundsFast(ray,
    // boxcopy.getCenter(auxD3), boxcopy.getDimensions(auxD4));
    // }
    // return observed;
    // }

    public LoadStatus getStatus() {
        return status;
    }

    public void setStatus(LoadStatus status) {
        synchronized (status) {
            this.status = status;
        }
    }

    /**
     * Sets the status to this node and its descendants recursively to the given
     * depth level.
     * 
     * @param status
     *            The new status.
     * @param depth
     *            The depth.
     */
    public void setStatus(LoadStatus status, int depth) {
        if (depth >= this.depth) {
            setStatus(status);
            for (int i = 0; i < 8; i++) {
                OctreeNode child = children[i];
                if (child != null) {
                    child.setStatus(status, depth);
                }
            }
        }
    }

    /**
     * Updates the number of objects, own objects and children. This operation
     * runs recursively in depth.
     */
    public void updateNumbers() {
        // Number of own objects
        this.ownObjects = objects != null ? objects.size : 0;

        // Number of recursive objects
        this.nObjects = this.ownObjects;

        // Children count
        this.childrenCount = 0;
        for (int i = 0; i < 8; i++) {
            if (children[i] != null) {
                this.childrenCount++;
                // Recursive call
                children[i].updateNumbers();
                nObjects += children[i].nObjects;
            }
        }

    }

    public int countObjects() {
        int n = 0;
        if (objects != null) {
            n += objects.size;
        }

        if (children != null)
            for (OctreeNode child : children) {
                if (child != null)
                    n += child.countObjects();
            }

        return n;
    }

    public OctreeNode findOctant(long id) {
        if (this.pageId == id)
            return this;
        else {
            if (this.children != null) {
                OctreeNode target = null;
                for (OctreeNode child : children) {
                    if (child != null) {
                        target = child.findOctant(id);
                        if (target != null)
                            return target;
                    }
                }
            }
        }
        return null;
    }

    com.badlogic.gdx.graphics.Color col = new com.badlogic.gdx.graphics.Color();

    @Override
    public void render(LineRenderSystem sr, ICamera camera, float alpha) {
        float maxDepth = OctreeNode.maxDepth * 2;
        // Colour depends on depth
        int rgb = 0xff000000 | ColourUtils.HSBtoRGB((float) depth / (float) maxDepth, 1f, 0.5f);

        alpha *= MathUtilsd.lint(depth, 0, maxDepth, 1.0, 0.5);

        this.col.set(ColourUtils.getRed(rgb) * alpha, ColourUtils.getGreen(rgb) * alpha, ColourUtils.getBlue(rgb) * alpha, alpha * opacity);

        if (this.observed) {
            this.col.set(Color.YELLOW);
        } else {
            this.col.set(Color.BROWN);
        }

        // Camera correction
        Vector3d loc = aux3d1;
        loc.set(this.blf).add(transform);

        /*
         * .·------· .' | .'| +---+--·' | | | | | | ,+--+---· |.' | .' +------+'
         */
        line(sr, loc.x, loc.y, loc.z, loc.x + size.x, loc.y, loc.z, this.col);
        line(sr, loc.x, loc.y, loc.z, loc.x, loc.y + size.y, loc.z, this.col);
        line(sr, loc.x, loc.y, loc.z, loc.x, loc.y, loc.z + size.z, this.col);

        /*
         * .·------· .' | .'| ·---+--+' | | | | | | ,·--+---+ |.' | .' ·------+'
         */
        line(sr, loc.x + size.x, loc.y, loc.z, loc.x + size.x, loc.y + size.y, loc.z, this.col);
        line(sr, loc.x + size.x, loc.y, loc.z, loc.x + size.x, loc.y, loc.z + size.z, this.col);

        /*
         * .·------+ .' | .'| ·---+--·' | | | | | | ,+--+---+ |.' | .' ·------·'
         */
        line(sr, loc.x + size.x, loc.y, loc.z + size.z, loc.x, loc.y, loc.z + size.z, this.col);
        line(sr, loc.x + size.x, loc.y, loc.z + size.z, loc.x + size.x, loc.y + size.y, loc.z + size.z, this.col);

        /*
         * .+------· .' | .'| ·---+--·' | | | | | | ,+--+---· |.' | .' ·------·'
         */
        line(sr, loc.x, loc.y, loc.z + size.z, loc.x, loc.y + size.y, loc.z + size.z, this.col);

        /*
         * .+------+ .' | .'| +---+--+' | | | | | | ,·--+---· |.' | .' ·------·'
         */
        line(sr, loc.x, loc.y + size.y, loc.z, loc.x + size.x, loc.y + size.y, loc.z, this.col);
        line(sr, loc.x, loc.y + size.y, loc.z, loc.x, loc.y + size.y, loc.z + size.z, this.col);
        line(sr, loc.x, loc.y + size.y, loc.z + size.z, loc.x + size.x, loc.y + size.y, loc.z + size.z, this.col);
        line(sr, loc.x + size.x, loc.y + size.y, loc.z, loc.x + size.x, loc.y + size.y, loc.z + size.z, this.col);

    }

    /** Draws a line **/
    private void line(LineRenderSystem sr, double x1, double y1, double z1, double x2, double y2, double z2, com.badlogic.gdx.graphics.Color col) {
        sr.addLine((float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, col);
    }

}
