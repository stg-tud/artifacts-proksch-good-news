/**
 * Copyright (c) 2011-2013 Darmstadt University of Technology.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Sebastian Proksch - initial API and implementation
 */
package cc.kave.histories;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

import cc.kave.histories.database.SqliteHistoryDatabase;
import cc.kave.histories.model.OUHistory;
import cc.kave.histories.model.OUSnapshot;
import cc.recommenders.names.ITypeName;
import cc.recommenders.usages.Query;
import cc.recommenders.usages.Usage;
import cc.recommenders.utils.gson.GsonUtil;

public class DoSomethingApp {

	public static void main(String[] args) throws SQLException, IOException {
		try (SqliteHistoryDatabase database = new SqliteHistoryDatabase("histories")) {
			Set<OUHistory> histories = database.getHistories();

			System.out.printf("%d histories found...", histories.size());

			int changes = 0;
			int removals = 0;
			int additions = 0;

			Map<ITypeName, Integer> counts = Maps.newLinkedHashMap();

			int i = 1;
			for (OUHistory h : histories) {
				OUSnapshot start = h.getStart();
				// System.out.println(start);
				Usage u = GsonUtil.deserialize(start.getUsageJson(), Query.class);
				OUSnapshot end = h.getEnd();
				// System.out.println(end);
				Usage v = GsonUtil.deserialize(end.getUsageJson(), Query.class);

				int calls1 = u.getReceiverCallsites().hashCode();
				int calls2 = v.getReceiverCallsites().hashCode();

				if (calls1 != calls2) {
					int delta = v.getReceiverCallsites().size() - u.getReceiverCallsites().size();

					if (delta < 0) {
						removals++;
						System.out.println("## method(s) removed ##############################");
					}
					if (delta == 0) {
						changes++;
						System.out.println("## method(s) changed ##############################");
					}
					if (delta > 0) {
						additions++;
						Integer count = counts.get(u.getType());
						if (count == null) {
							counts.put(u.getType(), 1);
						} else {
							counts.put(u.getType(), count + 1);
						}

						System.out.printf("## %d - delta: %d ##############################\n", i++, delta);
						System.out.println(u);
						System.out.println("-----------------------");
						System.out.println(v);
					}
				}
			}

			// Map<ITypeName, Integer> sortedCounts =
			// MapSorter.sortByCount(counts);
			// for (ITypeName type : sortedCounts.keySet()) {
			// System.out.printf("%5dx %s\n", sortedCounts.get(type), type);
			// }

			System.out.printf("%d removals, %d changes, %d additions (%d total)", removals, changes, additions,
					(removals + changes + additions));
		}
	}
}