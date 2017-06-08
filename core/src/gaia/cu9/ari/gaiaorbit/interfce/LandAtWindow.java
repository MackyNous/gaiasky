package gaia.cu9.ari.gaiaorbit.interfce;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.scenegraph.CelestialBody;
import gaia.cu9.ari.gaiaorbit.util.scene2d.OwnCheckBox;
import gaia.cu9.ari.gaiaorbit.util.scene2d.OwnLabel;
import gaia.cu9.ari.gaiaorbit.util.scene2d.OwnTextField;
import gaia.cu9.ari.gaiaorbit.util.validator.ExistingLocationValidator;
import gaia.cu9.ari.gaiaorbit.util.validator.FloatValidator;

public class LandAtWindow extends GenericDialog {

    private CelestialBody target;
    private CheckBox latlonCb, locationCb;

    private OwnTextField location, latitude, longitude;

    public LandAtWindow(CelestialBody target, Stage stage, Skin skin) {
	super(txt("context.landatcoord", target.getName()), skin, stage);
	this.target = target;

	setAcceptText(txt("gui.ok"));
	setCancelText(txt("gui.cancel"));

	// Build UI
	buildSuper();
    }

    @Override
    protected void build() {

	latlonCb = new OwnCheckBox(txt("context.lonlat"), skin, "radio", pad);
	latlonCb.setChecked(true);
	longitude = new OwnTextField("", skin, new FloatValidator(0, 360));
	latitude = new OwnTextField("", skin, new FloatValidator(-90, 90));

	locationCb = new OwnCheckBox(txt("context.location"), skin, "radio", pad);
	location = new OwnTextField("", skin, new ExistingLocationValidator(target));

	new ButtonGroup(latlonCb, locationCb);

	content.add(latlonCb).left().top().padBottom(pad).colspan(4).row();
	content.add(new OwnLabel(txt("context.longitude"), skin)).left().top().padRight(pad);
	content.add(longitude).left().top().padRight(pad * 2);
	content.add(new OwnLabel(txt("context.latitude"), skin)).left().top().padRight(pad);
	content.add(latitude).left().top().padBottom(pad * 2).row();

	content.add(locationCb).left().top().padBottom(pad).colspan(4).row();
	content.add(new OwnLabel(txt("context.location"), skin)).left().top().padRight(pad);
	content.add(location).left().top();

    }

    @Override
    protected void accept() {
	if (latlonCb.isChecked()) {
	    EventManager.instance.post(Events.LAND_AT_LOCATION_OF_OBJECT, target,
		    Double.parseDouble(longitude.getText()), Double.parseDouble(latitude.getText()));
	} else if (locationCb.isChecked()) {
	    EventManager.instance.post(Events.LAND_AT_LOCATION_OF_OBJECT, target, location.getText());
	}
    }

    @Override
    protected void cancel() {
    }

}