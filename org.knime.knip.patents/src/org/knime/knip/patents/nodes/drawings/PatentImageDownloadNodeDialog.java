package org.knime.knip.patents.nodes.drawings;

import org.knime.core.data.StringValue;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.knip.base.node.ValueToCellsNodeDialog;
import org.knime.knip.patents.nodes.AbstractPatentDownloadNodeModel;

public class PatentImageDownloadNodeDialog extends ValueToCellsNodeDialog<StringValue> {

	@Override
	public void addDialogComponents() {
		addDialogComponent(
				"Options",
				"OAuth2 Settings",
				new DialogComponentString(AbstractPatentDownloadNodeModel
						.createConsumerKeyModel(), "Consumer Key"));

		addDialogComponent(
				"Options",
				"OAuth2 Settings",
				new DialogComponentString(AbstractPatentDownloadNodeModel
						.createConsumerSecretModel(), "Consumer Secret"));
	}
}
