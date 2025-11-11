package com.pshakhlovich.coding.dsa;

public class HousePaint {

    public int minCostII(int[][] costs) {
        if (costs == null || costs.length == 0 || costs[0].length == 0) {
            return 0;
        }
        int n = costs.length;
        int k = costs[0].length;

        // Track the smallest and second smallest costs of the previous row,
        // and the color index of the smallest.
        int prevMin1 = 0, prevMin2 = 0, prevColor = -1;

        for (int i = 0; i < n; i++) {
            int currMin1 = Integer.MAX_VALUE;
            int currMin2 = Integer.MAX_VALUE;
            int currColor = -1;

            for (int j = 0; j < k; j++) {
                int cost = costs[i][j];
                // If we paint current house with the same color as the cheapest previous, we
                // must use second cheapest.
                cost += (j == prevColor ? prevMin2 : prevMin1);

                // Update current row's min1 and min2
                if (cost < currMin1) {
                    currMin2 = currMin1;
                    currMin1 = cost;
                    currColor = j;
                } else if (cost < currMin2) {
                    currMin2 = cost;
                }
            }

            // Move current row data to previous for next iteration
            prevMin1 = currMin1;
            prevMin2 = currMin2;
            prevColor = currColor;
        }

        return prevMin1;
    }

    public static void main(String[] args) {
        var sol = new HousePaint();

        // Example 1
        int[][] costs1 = { { 1, 5, 3 }, { 2, 9, 4 } };
        System.out.println("Input: [[1,5,3],[2,9,4]]");
        System.out.println("Output: " + sol.minCostII(costs1)); // Output: 5

        System.out.println("-----");

        // Example 2
        int[][] costs2 = { { 1, 3 }, { 2, 4 } };
        System.out.println("Input: [[1,3],[2,4]]");
        System.out.println("Output: " + sol.minCostII(costs2)); // Output: 5

        System.out.println("-----");

        // Additional Test Case: Single house
        int[][] costs3 = { { 10, 20 } };
        System.out.println("Input: [[10, 20]]");
        System.out.println("Output: " + sol.minCostII(costs3)); // Output: 10

        System.out.println("-----");

        // Additional Test Case: More houses and colors
        int[][] costs4 = { { 17, 2, 17 }, { 16, 16, 5 }, { 14, 3, 19 } };
        System.out.println("Input: [[17,2,17],[16,16,5],[14,3,19]]");
        System.out.println("Output: " + sol.minCostII(costs4)); // Output: 10 (2 + 5 + 3)

        System.out.println("-----");

        // Additional Test Case: k=1, n=1
        int[][] costs5 = { { 5 } };
        System.out.println("Input: [[5]]");
        System.out.println("Output: " + sol.minCostII(costs5)); // Output: 5

        // Additional Test Case: k=1, n=2 (Should be handled, though implies
        // impossibility)
        int[][] costs6 = { { 5 }, { 6 } };
        System.out.println("Input: [[5], [6]]");
        System.out.println("Output: " + sol.minCostII(costs6)); // Output: Integer.MAX_VALUE (or exception)
    }
}