from django.db import models


class Period(models.Model):
    name       = models.CharField(max_length=100, unique=True, db_column='name')
    start_date = models.DateField(db_column='start_date')
    end_date   = models.DateField(db_column='end_date')
    is_active  = models.BooleanField(default=True, db_column='is_active')

    class Meta:
        db_table = 'finance_period'
        ordering = ['-start_date']

    def __str__(self):
        return self.name


class Category(models.Model):
    GROUP_CHOICES = [
        ('ESSENTIAL', 'Essential'),
        ('LIFESTYLE',  'Lifestyle'),
        ('SAVINGS',    'Savings'),
        ('INCOME',     'Income'),
    ]
    name  = models.CharField(max_length=100, unique=True, db_column='name')
    group = models.CharField(max_length=100, choices=GROUP_CHOICES, db_column='"group"')

    class Meta:
        db_table = 'finance_category'
        ordering = ['group', 'name']
        verbose_name_plural = 'categories'

    def __str__(self):
        return self.name


class BudgetItem(models.Model):
    TYPE_CHOICES = [('IN', 'Income'), ('OUT', 'Expense')]

    period   = models.ForeignKey(Period,   on_delete=models.CASCADE, related_name='budget_items', db_column='period_id')
    category = models.ForeignKey(Category, on_delete=models.PROTECT,  related_name='budget_items', db_column='category_id')
    type             = models.CharField(max_length=3, choices=TYPE_CHOICES, db_column='type')
    projected_amount = models.DecimalField(max_digits=10, decimal_places=2, default=0, db_column='projected_amount')

    class Meta:
        db_table = 'finance_budget_item'
        constraints = [
            models.CheckConstraint(
                check=models.Q(projected_amount__gte=0),
                name='finance_budget_item_projected_amount_gte_0',
            ),
            models.UniqueConstraint(
                fields=['period', 'category'],
                name='finance_budget_item_period_category_unique',
            ),
        ]

    def __str__(self):
        return f'{self.period} / {self.category}'


class Transaction(models.Model):
    budget_item = models.ForeignKey(
        BudgetItem, on_delete=models.CASCADE,
        related_name='transactions', db_column='budget_item_id',
        null=True, blank=True,
    )
    date        = models.DateField(db_column='date')
    real_amount = models.DecimalField(max_digits=10, decimal_places=2, db_column='real_amount')
    description = models.CharField(max_length=255, db_column='description')
    notes       = models.TextField(null=True, blank=True, db_column='notes')

    class Meta:
        db_table = 'finance_transaction'
        ordering = ['-date']

    def __str__(self):
        return f'{self.date} {self.description}'


class StagingTransaction(models.Model):
    TYPE_CHOICES = [('IN', 'Income'), ('OUT', 'Expense')]

    source_file    = models.CharField(max_length=255, null=True, blank=True, db_column='source_file')
    account_number = models.CharField(max_length=50,  null=True, blank=True, db_column='account_number')
    original_date  = models.DateField(db_column='original_date')
    description    = models.CharField(max_length=255, db_column='description')
    doc_number     = models.CharField(max_length=20,  null=True, blank=True, db_column='doc_number')
    amount         = models.DecimalField(max_digits=10, decimal_places=2, db_column='amount')
    balance        = models.DecimalField(max_digits=14, decimal_places=2, null=True, blank=True, db_column='balance')
    type           = models.CharField(max_length=3, choices=TYPE_CHOICES, db_column='type')
    is_processed   = models.BooleanField(default=False, db_column='is_processed')
    assigned_category = models.ForeignKey(
        Category, on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='staging_transactions', db_column='assigned_category_id',
    )
    created_at = models.DateTimeField(auto_now_add=True, db_column='created_at')

    class Meta:
        db_table = 'finance_staging_transaction'
        ordering = ['-original_date', '-created_at']

    def __str__(self):
        return f'{self.original_date} {self.description}'


class StagingCCTransaction(models.Model):
    TYPE_CHOICES = [('IN', 'Payment / Credit'), ('OUT', 'Purchase / Charge')]

    source_file          = models.CharField(max_length=255,  null=True, blank=True, db_column='source_file')
    card_number          = models.CharField(max_length=30,   null=True, blank=True, db_column='card_number')
    card_holder          = models.CharField(max_length=100,  null=True, blank=True, db_column='card_holder')
    statement_date       = models.DateField(null=True, blank=True, db_column='statement_date')
    original_date        = models.DateField(db_column='original_date')
    description          = models.CharField(max_length=255,  db_column='description')
    location             = models.CharField(max_length=100,  null=True, blank=True, db_column='location')
    ref_code             = models.CharField(max_length=30,   null=True, blank=True, db_column='ref_code')
    amount               = models.DecimalField(max_digits=10, decimal_places=2, db_column='amount')
    installment_current  = models.SmallIntegerField(null=True, blank=True, db_column='installment_current')
    installment_total    = models.SmallIntegerField(null=True, blank=True, db_column='installment_total')
    installment_value    = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True, db_column='installment_value')
    type                 = models.CharField(max_length=3, choices=TYPE_CHOICES, db_column='type')
    is_processed         = models.BooleanField(default=False, db_column='is_processed')
    assigned_category    = models.ForeignKey(
        Category,
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='staging_cc_transactions',
        db_column='assigned_category_id',
    )
    created_at = models.DateTimeField(auto_now_add=True, db_column='created_at')

    class Meta:
        db_table = 'finance_staging_cc_transaction'
        ordering = ['-original_date', '-created_at']

    def __str__(self):
        return f'[CC] {self.original_date} {self.description}'
