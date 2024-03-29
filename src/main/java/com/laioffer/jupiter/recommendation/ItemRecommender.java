package com.laioffer.jupiter.recommendation;

import com.laioffer.jupiter.db.MySQLConnection;
import com.laioffer.jupiter.db.MySQLException;
import com.laioffer.jupiter.entity.Game;
import com.laioffer.jupiter.entity.Item;
import com.laioffer.jupiter.entity.ItemType;
import com.laioffer.jupiter.external.TwitchClient;
import com.laioffer.jupiter.external.TwitchException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ItemRecommender {
    private static final int DEFAULT_GAME_LIMIT = 3;
    private static final int DEFAULT_PER_GAME_RECOMMENDATION_LIMIT = 10;
    private static final int DEFAULT_TOTAL_RECOMMENDATION_LIMIT = 20;

    // Return a list of Item objects for the given type.
// Types are one of [Stream, Video, Clip].
// Add items are related to the top games provided in the argument
    private List<Item> recommendByTopGames(
            ItemType type, List<Game> topGames)
            throws RecommendationException {
        List<Item> recommendedItems = new ArrayList<>();
        TwitchClient client = new TwitchClient();

//        boolean reachedLimit = false;
        outerLoop:
        for (Game game : topGames) {
            List<Item> items;
            try {
                items = client.searchByType(game.getId(), type,
                        DEFAULT_PER_GAME_RECOMMENDATION_LIMIT);
            } catch (TwitchException e) {
                e.printStackTrace();
                throw new RecommendationException(
                        "Failed to get recommendation results");
            }
            for (Item item : items) {
                if (recommendedItems.size() ==
                        DEFAULT_TOTAL_RECOMMENDATION_LIMIT) {

                    break outerLoop;
                }
                recommendedItems.add(item);
            }

        }
//            if (reachedLimit == true) {
//                break;
//            }

//            while (DEFAULT_TOTAL_RECOMMENDATION_LIMIT
//            - recommendationItems.size()> 0 && items.size() > 0) {
//                recommendationItems.add(items.get(0));
//                items.remove(0);
//            }
//            if (recommendationItems.size() >= DEFAULT_TOTAL_RECOMMENDATION_LIMIT) {
//                break;
//            }
        return recommendedItems;
    }

    // Return a list of Item objects for the given type.
    // Types are one of [Stream, Video, Clip].
    // All items are related to the items previously favorited by the user.
    // E.g., if a user favorited some videos about game "Just Chatting",
    // then it will return some other videos about the same game.

    private List<Item> recommendByFavoriteHistory(
            Set<String> favoritedItemIds, List<String> favoriteGameIds,
            ItemType type) throws RecommendationException {
// Count the favorite game IDs from the database for the given user.
// E.g. if the favorited game ID list is ["1234", "2345", "2345", "3456"],
// the returned Map is {"1234": 1, "2345": 2, "3456": 1}
        Map<String, Long> favoriteGameIdByCount =
                favoriteGameIds.parallelStream()
                .collect(Collectors.groupingBy(
                        Function.identity(), Collectors.counting()
                ));
/*        Map<String, Long> favoriteGameIdByCount = new HashMap<>();
        for (String id : favoritedItemIds) {
            favoriteGameIdByCount.put(id,
                    favoriteGameIdByCount.getOrDefault(
                            id, 0) + 1);*/

        // Sort the game Id by count.
        // E.g. if the input is {"1234": 1, "2345": 2, "3456": 1},
        // the returned Map is {"2345": 2, "1234": 1, "3456": 1}
        List<Map.Entry<String, Long>> sortedFavoriteGameIdListByCount =
                new ArrayList<>(favoriteGameIdByCount.entrySet());
        sortedFavoriteGameIdListByCount.sort(
                (Map.Entry<String, Long> e1,
                Map.Entry<String, Long> e2) ->
                        Long.compare(e2.getValue(), e1.getValue())
        );
        if (sortedFavoriteGameIdListByCount.size() >
        DEFAULT_GAME_LIMIT) {
            sortedFavoriteGameIdListByCount =
                    sortedFavoriteGameIdListByCount.subList(
                            0, DEFAULT_GAME_LIMIT
                    );
        }
        List<Item> recommendedItems = new ArrayList<>();
        TwitchClient client = new TwitchClient();
        // Search Twitch based on the favorite game IDs
        // returned in the last step.
        outlerLoop:
        for (Map.Entry<String, Long> favoriteGame :
        sortedFavoriteGameIdListByCount) {
            List<Item> items;
            try {
                items = client.searchByType(
                        favoriteGame.getKey(), type,
                        DEFAULT_PER_GAME_RECOMMENDATION_LIMIT
                );
            } catch (TwitchException e) {
                e.printStackTrace();
                throw new RecommendationException(
                        "Failed to get recommendation results"
                );
            }

            for (Item item : items) {
                if (recommendedItems.size() ==
                        DEFAULT_TOTAL_RECOMMENDATION_LIMIT) {
                    break outlerLoop;
                }
                if (!favoritedItemIds.contains(item.getId())) {
                    recommendedItems.add(item);
                }
            }
        }
        return recommendedItems;

    }

    // Return a map of Item objects as the recommendation result.
    // Keys of the may are [Stream, Video, Clip].
    // Each key is corresponding to a list of Items objects,
    // each item object is a recommended item based on
    // the previous favorite records by the user.
    public Map<String, List<Item>> recommendItemsByUser(
            String userId) throws RecommendationException {
        Map<String, List<Item>> recommendedItemMap = new HashMap<>();
        Set<String> favoriteItemIds;
        Map<String, List<String>> favoriteGameIds;

        try (MySQLConnection conn = new MySQLConnection()) {
            favoriteItemIds = conn.getFavoriteItemIds(userId);
            favoriteGameIds = conn.getFavoriteGameIds(favoriteItemIds);

        } catch (MySQLException e) {
            e.printStackTrace();
            throw new RecommendationException(
                    "Failed to get user favorite history for recommendation"
            );
        }
        for (Map.Entry<String, List<String>> entry :
            favoriteGameIds.entrySet()) {

            if (entry.getValue().isEmpty()) {
                TwitchClient client = new TwitchClient();
                List<Game> topGames;
                try {
                    topGames = client.topGames(DEFAULT_GAME_LIMIT);
                } catch (TwitchException e) {
                    e.printStackTrace();
                    throw new RecommendationException(
                            "Failed to get top games for recommendation"
                    );
                }

                recommendedItemMap.put(
                        entry.getKey(),
                        recommendByTopGames(
                                ItemType.valueOf(entry.getKey()),
                                topGames)
                );
            } else {
                recommendedItemMap.put(
                        entry.getKey(),
                        recommendByFavoriteHistory(
                                favoriteItemIds, entry.getValue(),
                                ItemType.valueOf(entry.getKey()))

                );
            }

        }
        return  recommendedItemMap;
    }
    // Return a map of Item objects as the recommendation result.
    // Keys of the may are [Stream, Video, Clip].
    // Each key is corresponding to a list of Items objects,
    // each item object is a recommended item based on the top games
    // currently on Twitch.
    public Map<String, List<Item>> recommendItemsByDefault()
            throws RecommendationException {
        Map<String, List<Item>> recommendedItemMap = new HashMap<>();
        TwitchClient client = new TwitchClient();
        List<Game> topGames;
        try {
            topGames = client.topGames(DEFAULT_GAME_LIMIT);
        } catch (TwitchException e) {
            e.printStackTrace();
            throw new RecommendationException(
                    "Failed to get game data for recommendation");
        }

        for (ItemType type : ItemType.values()) {
            recommendedItemMap.put(
                    type.toString(), recommendByTopGames(type, topGames));
        }
        return recommendedItemMap;
    }


}
