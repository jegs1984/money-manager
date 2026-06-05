from django.db import models
from django.core.validators import MinValueValidator
from decimal import Decimal


class BudgetMonth(models.Model):
    id = models.BigAutoField(primary_key=True)
    month_date = models.DateField(unique=True, db_index=True)
    is_active = models.BooleanField(default=True)

    class Meta:
        db_table = 'finance_budget_month'
        ordering = ['-month_date']

    def __str__(self):
        return f"BudgetMonth {self.month_date}"


class Category(models.Model):
    GROUP_CHOICES = [
        ('ESSENTIAL', 'Essential'),
        ('LIFESTYLE', 'Lifestyle'),
        ('SAVINGS', 'Savings'),
        ('INCOME', 'Income'),
    ]

    id = models.BigAutoField(primary_key=True)
    name = models.CharField(max_length=100, unique=True, db_index=True)
    group = models.CharField(max_length=100, choices=GROUP_CHOICES)

    class Meta:
        db_table = 'finance_category'
        ordering = ['group', 'name']

    def __str__(self):
        return self.name


class BudgetItem(models.Model):
    TYPE_CHOICES = [
        ('IN', 'Income'),
        ('OUT', 'Expense'),
    ]

    id = models.BigAutoField(primary_key=True)
    budget_month = models.ForeignKey(
        BudgetMonth,
        on_delete=models.CASCADE,
        db_column='budget_month_id',
        related_name='budget_items'
    )
    category = models.ForeignKey(
        Category,
        on_delete=models.PROTECT,
        db_column='category_id',
        related_name='budget_items'
    )
    type = models.CharField(max_length=3, choices=TYPE_CHOICES, db_column='type')
    projected_amount = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        default=Decimal('0.00'),
        validators=[MinValueValidator(Decimal('0.00'))]
    )

    class Meta:
        db_table = 'finance_budget_item'
        constraints = [
            models.CheckConstraint(
                check=models.Q(projected_amount__gte=0),
                name='budget_item_projected_amount_non_negative'
            ),
            models.UniqueConstraint(
                fields=['budget_month', 'category'],
                name='budget_item_budget_month_category_unique'
            ),
        ]
        indexes = [
            models.Index(fields=['budget_month']),
            models.Index(fields=['category']),
        ]

    def __str__(self):
        return f"{self.budget_month} - {self.category} ({self.type})"


class Transaction(models.Model):
    id = models.BigAutoField(primary_key=True)
    budget_item = models.ForeignKey(
        BudgetItem,
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        db_column='budget_item_id',
        related_name='transactions'
    )
    date = models.DateField(db_index=True)
    real_amount = models.DecimalField(max_digits=10, decimal_places=2)
    description = models.CharField(max_length=255)
    notes = models.TextField(null=True, blank=True)

    class Meta:
        db_table = 'finance_transaction'
        ordering = ['-date']
        indexes = [
            models.Index(fields=['date']),
            models.Index(fields=['budget_item']),
        ]

    def __str__(self):
        return f"{self.date} - {self.description} ({self.real_amount})"


class StagingTransaction(models.Model):
    TYPE_CHOICES = [
        ('IN', 'Income'),
        ('OUT', 'Expense'),
    ]

    id = models.BigAutoField(primary_key=True)
    original_date = models.DateField()
    description = models.CharField(max_length=255)
    amount = models.DecimalField(max_digits=10, decimal_places=2)
    type = models.CharField(max_length=3, choices=TYPE_CHOICES)
    is_processed = models.BooleanField(default=False, db_index=True)
    assigned_category = models.ForeignKey(
        Category,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        db_column='assigned_category_id',
        related_name='staging_transactions'
    )

    class Meta:
        db_table = 'finance_staging_transaction'
        ordering = ['-original_date']
        indexes = [
            models.Index(fields=['is_processed']),
            models.Index(fields=['assigned_category']),
        ]

    def __str__(self):
        return f"{self.original_date} - {self.description}"
