import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
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

transaction_time = pd.read_csv('test-results/transaction_processing_time.csv')
double_spending = pd.read_csv('test-results/double_spending_detection_time.csv')
memory_usage = pd.read_csv('test-results/memory_usage.csv')
false_positive = pd.read_csv('test-results/false_positive_rate.csv')

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
