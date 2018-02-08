#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

// Attributes
attribute vec4 a_position;
attribute vec2 a_texCoord0;

// Uniforms
uniform mat4 u_projTrans;
uniform vec4 u_color;
uniform vec4 u_quaternion;
uniform vec3 u_pos;
uniform float u_size;
uniform float u_apparent_angle;
uniform float u_th_angle_point;
uniform vec3 u_camShift;
uniform int u_relativsiticAberration; // Relativistic aberration flag
uniform vec3 u_velDir; // Velocity vector
uniform float u_vc; // Fraction of the speed of light, v/c

// Varyings
varying vec4 v_color;
varying vec2 v_texCoords;

<INCLUDE shader/lib_math.glsl>

<INCLUDE shader/lib_geometry.glsl>


void main()
{
   float alpha = min(1.0, lint(u_apparent_angle, u_th_angle_point, u_th_angle_point * 4.0, 0.0, 1.0));

   v_color = vec4(u_color.rgb, u_color.a * alpha);
   v_texCoords = a_texCoord0;
   
   mat4 transform = u_projTrans;
   
   vec3 pos = u_pos - u_camShift;
   
   if(u_relativsiticAberration == 1) {
       // Relativistic aberration
       // Current cosine of angle cos(th_s) cos A = DotProduct(v1, v2) / (Length(v1) * Length(v2))
       float dist = length(pos);
       vec3 cdir = u_velDir * -1.0;
       float costh_s = dot(cdir, pos) / dist;
       float th_s = acos(costh_s);
       float costh_o = (costh_s - u_vc) / (1 - u_vc * costh_s);
       float th_o = acos(costh_o);
       pos = rotate_vertex_position(pos, normalize(cross(cdir, pos)), th_o - th_s);
   }
   
   // Translate
   mat4 translate = mat4(1.0);
   
   translate[3][0] = pos.x;
   translate[3][1] = pos.y;
   translate[3][2] = pos.z;
   translate[3][3] = 1.0;
   transform *= translate;
   
   // Rotate
   mat4 rotation = mat4(0.0);
   float xx = u_quaternion.x * u_quaternion.x;
   float xy = u_quaternion.x * u_quaternion.y;
   float xz = u_quaternion.x * u_quaternion.z;
   float xw = u_quaternion.x * u_quaternion.w;
   float yy = u_quaternion.y * u_quaternion.y;
   float yz = u_quaternion.y * u_quaternion.z;
   float yw = u_quaternion.y * u_quaternion.w;
   float zz = u_quaternion.z * u_quaternion.z;
   float zw = u_quaternion.z * u_quaternion.w;
   
   rotation[0][0] = 1.0 - 2.0 * (yy + zz);
   rotation[1][0] = 2.0 * (xy - zw);
   rotation[2][0] = 2.0 * (xz + yw);
   rotation[0][1] = 2.0 * (xy + zw);
   rotation[1][1] = 1.0 - 2.0 * (xx + zz);
   rotation[2][1] = 2.0 * (yz - xw);
   rotation[3][1] = 0.0;
   rotation[0][2] = 2.0 * (xz - yw);
   rotation[1][2] = 2.0 * (yz + xw);
   rotation[2][2] = 1.0 - 2.0 * (xx + yy);
   rotation[3][3] = 1.0;
   transform *= rotation;
   
   // Scale
   transform[0][0] *= u_size;
   transform[1][1] *= u_size;
   transform[2][2] *= u_size;
   
   // Position
   gl_Position =  transform * a_position;
}
