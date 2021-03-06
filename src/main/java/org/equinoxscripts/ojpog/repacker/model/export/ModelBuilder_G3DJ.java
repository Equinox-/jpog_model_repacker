package org.equinoxscripts.ojpog.repacker.model.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;

import org.equinoxscripts.ojpog.io.VectorUtils;
import org.equinoxscripts.ojpog.io.tmd.TMD_File;
import org.equinoxscripts.ojpog.io.tmd.anim.TMD_Animation;
import org.equinoxscripts.ojpog.io.tmd.anim.TMD_Channel;
import org.equinoxscripts.ojpog.io.tmd.anim.TMD_KeyFrame;
import org.equinoxscripts.ojpog.io.tmd.mesh.TMD_Mesh;
import org.equinoxscripts.ojpog.io.tmd.mesh.TMD_Mesh_Piece;
import org.equinoxscripts.ojpog.io.tmd.mesh.TMD_Vertex;
import org.equinoxscripts.ojpog.io.tmd.scene.TMD_Node;
import org.equinoxscripts.ojpog.repacker.Base;
import org.equinoxscripts.ojpog.repacker.G3DModelWriter;
import org.json.simple.JSONObject;

import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;

public class ModelBuilder_G3DJ {
	public static final boolean enableSkinning = true;

	public static void write(String id, TMD_File file) throws IOException {
		ModelData model = new ModelData();
		model.id = id;
		model.version[0] = 0;
		model.version[1] = 1;
		ModelNode[] mns = new ModelNode[file.nodes.nodes.length];
		Map<TMD_Node, ModelNode> modelNodes = new HashMap<>();
		for (int i = 0; i < file.nodes.nodes.length; i++) {
			TMD_Node n = file.nodes.nodes[i];
			ModelNode mn = mns[i] = new ModelNode();
			modelNodes.put(n, mn);
			mn.id = n.node_name;

			if (n.parent < 0 || n.parent >= file.nodes.nodes.length) {
				model.nodes.add(mn);
			}
			mn.translation = new Vector3();
			mn.rotation = new Quaternion();
			Matrix4f localPos = n.localPosition();
			mn.translation.set(localPos.m03, localPos.m13, localPos.m23);
			Quat4f q4 = VectorUtils.matrixToQuat(localPos);
			mn.rotation = new Quaternion();
			mn.rotation.x = q4.x;
			mn.rotation.y = q4.y;
			mn.rotation.z = q4.z;
			mn.rotation.w = q4.w;
		}

		for (int i = 0; i < file.nodes.nodes.length; i++) {
			TMD_Node n = file.nodes.nodes[i];
			if (n.parent >= 0 && n.parent < file.nodes.nodes.length) {
				ModelNode[] ch = mns[n.parent].children;
				if (ch == null)
					mns[n.parent].children = new ModelNode[] { mns[i] };
				else {
					ch = mns[n.parent].children = Arrays.copyOf(ch, ch.length + 1);
					ch[ch.length - 1] = mns[i];
				}
			}
		}

		Set<String> mats = new HashSet<String>();
		{
			ModelNode root = model.nodes.get(0);
			int aliasOffset = root.children.length;
			int meshCount = file.dLoD.levels[0].members.length;

			root.children = Arrays.copyOf(root.children, root.children.length + meshCount);
			Vector3 aliasTranslation;
			Quaternion aliasRotation;
			{
				Matrix4 inv = new Matrix4().set(root.translation, root.rotation).inv();
				inv.getTranslation(aliasTranslation = new Vector3());
				inv.getRotation(aliasRotation = new Quaternion());
			}

			int meshID = 0;
			for (TMD_Mesh mesh : file.dLoD.levels[0].members) {
				mats.add(mesh.material_name);
				model.meshes.add(makeMesh(mesh));

				ModelNode alias = root.children[aliasOffset + meshID] = new ModelNode();
				alias.id = "mesh_alias_" + mesh.hashCode();
				alias.translation = new Vector3(aliasTranslation);
				alias.rotation = new Quaternion(aliasRotation);
				alias.meshId = "m_" + mesh.hashCode();

				alias.parts = new ModelNodePart[mesh.pieces.length];
				for (int pieceID = 0; pieceID < mesh.pieces.length; pieceID++) {
					TMD_Mesh_Piece piece = mesh.pieces[pieceID];
					ModelNodePart part = alias.parts[pieceID] = new ModelNodePart();

					part.meshPartId = "mp_" + piece.hashCode();
					part.materialId = mesh.material_name;
					if (enableSkinning) {
						part.bones = new ArrayMap<>();
						for (int i = 0; i < piece.meshParents.length; i++) {
							TMD_Node node = file.nodes.nodes[piece.meshParents[i]];
							Matrix4 m4 = new Matrix4();
							for (int r = 0; r < 4; r++)
								for (int c = 0; c < 4; c++)
									m4.val[r + c * 4] = node.worldSkinningMatrix.getElement(r, c);
							part.bones.put(node.node_name, m4);
						}
					}
				}
				meshID++;
			}
		}

		for (String mat : mats) {
			ModelMaterial def = new ModelMaterial();
			def.id = mat;
			ModelTexture tex = new ModelTexture();
			tex.fileName = mat + "_0.dds";
			tex.id = mat;
			tex.usage = ModelTexture.USAGE_DIFFUSE;
			def.textures = new Array<ModelTexture>();
			def.textures.add(tex);
			model.materials.add(def);
		}

		for (TMD_Animation a : file.animations.animations) {
			ModelAnimation anim = new ModelAnimation();
			anim.id = a.name;
			anim.nodeAnimations = new Array<>();
			for (TMD_Channel c : a.channelNodeMap) {
				if (c.nodeRef == null || c.shouldIgnore())
					continue;
				ModelNodeAnimation mna = new ModelNodeAnimation();
				mna.nodeId = modelNodes.get(c.nodeRef).id;
				mna.translation = new Array<>();
				mna.rotation = new Array<>();
				for (TMD_KeyFrame frame : c.frames) {
					ModelNodeKeyframe<Vector3> pos = new ModelNodeKeyframe<>();
					pos.keytime = frame.time;
					pos.value = new Vector3(frame.localPos.x, frame.localPos.y, frame.localPos.z);

					ModelNodeKeyframe<Quaternion> rot = new ModelNodeKeyframe<>();
					rot.keytime = frame.time;
					rot.value = new Quaternion(frame.localRot.x, frame.localRot.y, frame.localRot.z, frame.localRot.w);

					mna.translation.add(pos);
					mna.rotation.add(rot);
				}
				anim.nodeAnimations.add(mna);
			}
			model.animations.add(anim);
		}

		File out = new File(Base.BASE_OUT, "Data/Models/" + id + ".g3dj");
		out.getParentFile().mkdirs();
		JSONObject ff = new JSONObject();
		G3DModelWriter w = new G3DModelWriter(ff);
		w.put(model);

		Writer ww = new FileWriter(out);
		ff.writeJSONString(ww);
		ww.close();
//		G3dModelLoader loader = new G3dModelLoader(new JsonReader());
//		ModelData output = loader.loadModelData(new FileHandle(out));
	}

	public static ModelMesh makeMesh(TMD_Mesh m) {
		m.loadVtxAndTri();
		ModelMesh mm = new ModelMesh();
		mm.id = "m_" + m.hashCode();
		int vsize;
		if (m.isSkinned() && enableSkinning) {
			mm.attributes = new VertexAttribute[3 + m.maxBindingsPerVertex];
			mm.attributes[0] = VertexAttribute.Position();
			mm.attributes[1] = VertexAttribute.Normal();
			mm.attributes[2] = VertexAttribute.TexCoords(0);
			for (int i = 0; i < m.maxBindingsPerVertex; i++)
				mm.attributes[3 + i] = VertexAttribute.BoneWeight(i);
			vsize = 3 + 3 + 2 + 2 * m.maxBindingsPerVertex;
		} else {
			mm.attributes = new VertexAttribute[] { VertexAttribute.Position(), VertexAttribute.Normal(),
					VertexAttribute.TexCoords(0) };
			vsize = 3 + 3 + 2;
		}
		mm.vertices = new float[vsize * m.verts.length];
		for (int i = 0; i < m.verts.length; i++) {
			TMD_Vertex v = m.verts[i];
			int o = vsize * i;
			mm.vertices[o] = v.position.x;
			mm.vertices[o + 1] = v.position.y;
			mm.vertices[o + 2] = v.position.z;
			mm.vertices[o + 3] = v.normal.x;
			mm.vertices[o + 4] = v.normal.y;
			mm.vertices[o + 5] = v.normal.z;
			mm.vertices[o + 6] = v.texpos.x;
			mm.vertices[o + 7] = v.texpos.y;
			if (m.isSkinned() && enableSkinning) {
				List<Entry<Integer, Float>> lst = new ArrayList<>(v.weightsBoneID().entrySet());
				for (int b = 0; b < m.maxBindingsPerVertex; b++) {
					int bid = 0;
					float bw = 0;
					if (b < lst.size()) {
						Entry<Integer, Float> l = lst.get(b);
						bid = l.getKey().intValue();
						bw = l.getValue().floatValue();
					}
					mm.vertices[o + 8 + b * 2] = bid;
					mm.vertices[o + 9 + b * 2] = bw;
				}
			}
		}

		mm.parts = new ModelMeshPart[m.pieces.length];
		for (int i = 0; i < m.pieces.length; i++) {
			mm.parts[i] = new ModelMeshPart();
			mm.parts[i].id = "mp_" + m.pieces[i].hashCode();
			mm.parts[i].primitiveType = GL30.GL_TRIANGLE_STRIP;
			mm.parts[i].indices = Arrays.copyOf(m.pieces[i].tri_strip, m.pieces[i].tri_strip.length);
		}
		return mm;
	}
}
