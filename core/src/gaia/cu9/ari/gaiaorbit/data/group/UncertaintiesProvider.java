package gaia.cu9.ari.gaiaorbit.data.group;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import gaia.cu9.ari.gaiaorbit.util.Constants;
import gaia.cu9.ari.gaiaorbit.util.I18n;
import gaia.cu9.ari.gaiaorbit.util.Logger;
import gaia.cu9.ari.gaiaorbit.util.coord.Coordinates;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;
import gaia.cu9.ari.gaiaorbit.util.parse.Parser;

public class UncertaintiesProvider implements IParticleGroupDataProvider {

    @Override
    public List<double[]> loadData(String file) {
        return loadData(file, 1);
    }

    @Override
    public List<double[]> loadData(String file, double factor) {

        List<double[]> pointData = new ArrayList<double[]>();
        FileHandle f = Gdx.files.internal(file);
        try {
            int tokenslen;
            BufferedReader br = new BufferedReader(new InputStreamReader(f.read()));
            String line;
            Vector3d pos = new Vector3d();
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // Read line
                    String[] tokens = line.split("\\s+");
                    tokenslen = tokens.length;
                    double[] point = new double[tokenslen];
                    for (int j = 0; j < tokenslen; j++) {
                        point[j] = Parser.parseDouble(tokens[j]) * factor;
                    }

                    pos.set(point[1], point[2], (point[0] + 8));
                    pos.mul(Coordinates.equatorialToGalactic());
                    pos.scl(Constants.KPC_TO_U);

                    point[0] = pos.x;
                    point[1] = pos.y;
                    point[2] = pos.z;

                    pointData.add(point);
                }
            }

            br.close();

            Logger.info(this.getClass().getSimpleName(), I18n.bundle.format("notif.nodeloader", pointData.size(), file));
        } catch (Exception e) {
            Logger.error(e, PointDataProvider.class.getName());
        }

        return pointData;
    }
}