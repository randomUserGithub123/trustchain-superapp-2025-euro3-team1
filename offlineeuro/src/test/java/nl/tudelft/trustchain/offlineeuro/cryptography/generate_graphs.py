import pandas as pd
import matplotlib.pyplot as plt
import os

plt.style.use('seaborn-v0_8-whitegrid')
os.makedirs('test-results/graphs', exist_ok=True)

def plot_graph(data, x_col, y_cols, title, filename, ylabel, log_scale=False):
    plt.figure(figsize=(10, 6))
    for col in y_cols:
        plt.plot(data[x_col], data[col], marker='o', label=col)
    plt.xlabel('Number of Euros')
    plt.ylabel(ylabel)
    plt.title(title)
    plt.legend()
    plt.grid(True, alpha=0.3)
    
    y_min = min(data[y_cols].min())
    y_max = max(data[y_cols].max())
    
    # Padding
    y_min = y_min * 0.9
    y_max = y_max * 1.1
    
    plt.ylim(y_min, y_max)
    
    if log_scale:
        plt.yscale('log')
        plt.grid(True, which="both", ls="-", alpha=0.2)
    
    plt.savefig(f'test-results/graphs/{filename}.png', dpi=300, bbox_inches='tight')
    plt.close()

# Read existing data
try:
    transaction_time = pd.read_csv('test-results/transaction_processing_time.csv')
    double_spending = pd.read_csv('test-results/double_spending_detection_time.csv')
    memory_usage = pd.read_csv('test-results/memory_usage.csv')
    false_positive = pd.read_csv('test-results/false_positive_rate.csv')
    
    # Plot existing graphs
    # Plot transaction processing time
    plot_graph(
        transaction_time,
        'Size',
        ['OldMethodTime(ms)', 'BloomFilterTime(ms)'],
        'Transaction Processing Time Comparison',
        'transaction_processing_time',
        'Time (ms)'
    )

    # Plot double spending detection time
    plot_graph(
        double_spending,
        'Size',
        ['OldMethodTime(ms)', 'BloomFilterTime(ms)'],
        'Double Spending Detection Time Comparison',
        'double_spending_detection_time',
        'Time (ms)'
    )

    # Plot memory usage log
    plot_graph(
        memory_usage,
        'Size',
        ['OldMethodMemory(bytes)', 'BloomFilterMemory(bytes)'],
        'Memory Usage Comparison',
        'memory_usage',
        'Memory Usage (bytes) (log scale)',
        log_scale=True
    )

    # Plot memory usage linear
    plot_graph(
        memory_usage,
        'Size',
        ['OldMethodMemory(bytes)', 'BloomFilterMemory(bytes)'],
        'Memory Usage Comparison',
        'memory_usage',
        'Memory Usage (bytes)',
    )

    # Plot false positive rate
    plot_graph(
        false_positive,
        'Size',
        ['FalsePositiveRate'],
        'Bloom Filter False Positive Rate',
        'false_positive_rate',
        'False Positive Rate'
    )
except FileNotFoundError:
    print("Some existing CSV files not found, skipping existing plots")

# Read and plot new performance test data
try:
    # Double spending detection performance (new token)
    double_spending_perf = pd.read_csv('test-results/double_spending_detection_performance.csv')
    plot_graph(
        double_spending_perf,
        'Size',
        ['LinearSearchTime(micros)', 'BloomFilterTime(micros)'],
        'Double Spending Detection Performance (New Token)',
        'double_spending_detection_performance',
        'Time (microseconds)',
        log_scale=True
    )
    
    # Plot speedup factor
    plt.figure(figsize=(10, 6))
    plt.plot(double_spending_perf['Size'], double_spending_perf['SpeedupFactor'], marker='o', color='green', linewidth=2)
    plt.xlabel('Number of Euros')
    plt.ylabel('Speedup Factor (x)')
    plt.title('BloomFilter Speedup Factor vs Linear Search')
    plt.grid(True, alpha=0.3)
    plt.yscale('log')
    plt.savefig('test-results/graphs/double_spending_speedup_factor.png', dpi=300, bbox_inches='tight')
    plt.close()
    
except FileNotFoundError:
    print("double_spending_detection_performance.csv not found")

try:
    # Double spending detection with existing token
    double_spending_existing = pd.read_csv('test-results/double_spending_detection_existing_token.csv')
    plot_graph(
        double_spending_existing,
        'Size',
        ['LinearSearchTime(micros)', 'BloomFilterTime(micros)'],
        'Double Spending Detection Performance (Existing Token)',
        'double_spending_detection_existing_token',
        'Time (microseconds)',
        log_scale=True
    )
    
    # Plot speedup factor for existing token
    plt.figure(figsize=(10, 6))
    plt.plot(double_spending_existing['Size'], double_spending_existing['SpeedupFactor'], marker='s', color='red', linewidth=2)
    plt.xlabel('Number of Euros')
    plt.ylabel('Speedup Factor (x)')
    plt.title('BloomFilter Speedup Factor vs Linear Search (Existing Token)')
    plt.grid(True, alpha=0.3)
    plt.yscale('log')
    plt.savefig('test-results/graphs/double_spending_speedup_factor_existing.png', dpi=300, bbox_inches='tight')
    plt.close()
    
except FileNotFoundError:
    print("double_spending_detection_existing_token.csv not found")

try:
    # Hash creation performance
    hash_creation = pd.read_csv('test-results/hash_creation_performance.csv')
    plot_graph(
        hash_creation,
        'Size',
        ['HashCreationTime(micros)', 'LinearSearchTime(micros)'],
        'Hash Creation vs Linear Search Performance',
        'hash_creation_performance',
        'Time (microseconds)',
        log_scale=True
    )
    
    # Plot hash vs linear ratio
    plt.figure(figsize=(10, 6))
    plt.plot(hash_creation['Size'], hash_creation['HashVsLinearRatio'], marker='^', color='purple', linewidth=2)
    plt.xlabel('Number of Euros')
    plt.ylabel('Hash Creation Time / Linear Search Time')
    plt.title('Hash Creation Efficiency vs Linear Search')
    plt.grid(True, alpha=0.3)
    plt.axhline(y=1, color='red', linestyle='--', alpha=0.7, label='Equal Performance')
    plt.legend()
    plt.savefig('test-results/graphs/hash_creation_ratio.png', dpi=300, bbox_inches='tight')
    plt.close()
    
except FileNotFoundError:
    print("hash_creation_performance.csv not found")

# Create a comprehensive comparison plot if all data is available
try:
    double_spending_perf = pd.read_csv('test-results/double_spending_detection_performance.csv')
    double_spending_existing = pd.read_csv('test-results/double_spending_detection_existing_token.csv')
    
    plt.figure(figsize=(12, 8))
    
    # Plot both speedup factors
    plt.subplot(2, 1, 1)
    plt.plot(double_spending_perf['Size'], double_spending_perf['SpeedupFactor'], marker='o', label='New Token', linewidth=2)
    plt.plot(double_spending_existing['Size'], double_spending_existing['SpeedupFactor'], marker='s', label='Existing Token', linewidth=2)
    plt.xlabel('Number of Euros')
    plt.ylabel('Speedup Factor (x)')
    plt.title('BloomFilter Performance Improvement')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.yscale('log')
    
    # Plot absolute times
    plt.subplot(2, 1, 2)
    plt.plot(double_spending_perf['Size'], double_spending_perf['LinearSearchTime(micros)'], marker='o', label='Linear Search', linewidth=2)
    plt.plot(double_spending_perf['Size'], double_spending_perf['BloomFilterTime(micros)'], marker='s', label='BloomFilter', linewidth=2)
    plt.xlabel('Number of Euros')
    plt.ylabel('Time (microseconds)')
    plt.title('Absolute Performance Comparison')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.yscale('log')
    
    plt.tight_layout()
    plt.savefig('test-results/graphs/comprehensive_performance_comparison.png', dpi=300, bbox_inches='tight')
    plt.close()
    
except FileNotFoundError:
    print("Cannot create comprehensive comparison - missing data files")

print("Graph generation complete!")
