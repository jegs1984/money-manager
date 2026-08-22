from django.db import models
from django.core.exceptions import ValidationError
from django.db.models import Q
from django.contrib.postgres.constraints import ExclusionConstraint
from django.contrib.postgres.fields import DateRangeField, RangeOperators


class Period(models.Model):
    name       = models.CharField(max_length=100, unique=True, db_column='name')
    start_date = models.DateField(db_column='start_date')
    end_date   = models.DateField(db_column='end_date')
    is_active  = models.BooleanField(default=True, db_column='is_active')
    closed_at  = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'finance_period'
        ordering = ['-start_date']
        indexes = [models.Index(fields=['start_date'], name='period_start_date_idx'), models.Index(fields=['end_date'], name='period_end_date_idx')]
        constraints = [
            models.CheckConstraint(
                check=Q(start_date__lte=models.F('end_date')),
                name='finance_period_start_before_end',
            ),
            models.UniqueConstraint(
                fields=['is_active'],
                condition=Q(is_active=True),
                name='finance_period_single_active',
            ),
            ExclusionConstraint(
                name='finance_period_dates_do_not_overlap',
                expressions=[
                    (
                        models.Func(
                            'start_date', 'end_date', models.Value('[]'),
                            function='DATERANGE', output_field=DateRangeField(),
                        ),
                        RangeOperators.OVERLAPS,
                    ),
                ],
            ),
        ]

    def __str__(self):
        return self.name

    def clean(self):
        super().clean()
        if self.start_date and self.end_date and self.start_date > self.end_date:
            raise ValidationError({'end_date': 'The end date must be on or after the start date.'})
        if self.start_date and self.end_date and Period.objects.exclude(pk=self.pk).filter(
            start_date__lte=self.end_date,
            end_date__gte=self.start_date,
        ).exists():
            raise ValidationError('Periods may not overlap. Each transaction date must map to one period.')


class Account(models.Model):
    KIND_CHOICES = [
        ('CHECKING', 'Checking account'),
        ('SAVINGS', 'Savings account'),
        ('CASH', 'Cash'),
        ('CREDIT_CARD', 'Credit card'),
    ]

    name = models.CharField(max_length=100, unique=True)
    kind = models.CharField(max_length=20, choices=KIND_CHOICES)
    external_reference = models.CharField(max_length=100, blank=True)
    opening_balance = models.DecimalField(max_digits=14, decimal_places=2, default=0)
    is_active = models.BooleanField(default=True)

    class Meta:
        db_table = 'finance_account'
        ordering = ['name']
        constraints = [
            models.UniqueConstraint(
                fields=['external_reference'],
                condition=~Q(external_reference=''),
                name='finance_account_external_reference_unique',
            ),
        ]

    def __str__(self):
        return self.name


class Category(models.Model):
    GROUP_CHOICES = [
        ('AhorroeInversion', 'Ahorro e Inversión'),
        ('Alimentación', 'Alimentación'),
        ('Cuenta', 'Cuenta'),
        ('CuentaVina', 'Cuenta Viña'),
        ('Deuda', 'Deuda'),
        ('Gastos', 'Gastos'),
        ('GastosExtraordinarios', 'Gastos Extraordinarios'),
        ('IngresoFijo', 'Ingreso Fijo'),
        ('IngresoVariableExtra', 'Ingreso Variable / Extra'),
        ('Lujo', 'Lujo'),
        ('Pension', 'Pension'),
        ('Personal', 'Personal'),
        ('Transporte', 'Transporte'),
        ('ViviendaHogar', 'Vivienda / Hogar')
    ]
    name  = models.CharField(max_length=100, unique=True, db_column='name')
    group = models.CharField(max_length=100, choices=GROUP_CHOICES, db_column='"group"')

    class Meta:
        db_table = 'finance_category'
        ordering = ['group', 'name']
        verbose_name_plural = 'categories'
        indexes = [models.Index(fields=['name'], name='category_name_idx')]

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
        indexes = [models.Index(fields=['period'], name='budget_item_period_idx'), models.Index(fields=['category'], name='budget_item_category_idx')]
        constraints = [
            models.CheckConstraint(
                check=models.Q(projected_amount__gte=0),
                name='finance_budget_item_projected_amount_gte_0',
            ),
            models.UniqueConstraint(
                fields=['period', 'category', 'type'],
                name='finance_budget_item_period_category_type_unique',
            ),
        ]

    def __str__(self):
        return f'{self.period} / {self.category}'


class Transaction(models.Model):
    account = models.ForeignKey(
        Account, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='transactions',
    )
    budget_item = models.ForeignKey(
        BudgetItem, on_delete=models.CASCADE,
        related_name='transactions', db_column='budget_item_id',
        null=True, blank=True,
    )
    date        = models.DateField(db_column='date')
    real_amount = models.DecimalField(max_digits=10, decimal_places=2, db_column='real_amount')
    description = models.CharField(max_length=255, db_column='description')
    notes       = models.TextField(null=True, blank=True, db_column='notes')
    source_staging_transaction = models.OneToOneField(
        'StagingTransaction', on_delete=models.PROTECT,
        related_name='committed_transaction', null=True, blank=True,
    )
    source_staging_cc_transaction = models.OneToOneField(
        'StagingCCTransaction', on_delete=models.PROTECT,
        related_name='committed_transaction', null=True, blank=True,
    )
    source_fingerprint = models.CharField(max_length=160, blank=True, db_index=True)
    reversal_of = models.OneToOneField(
        'self', on_delete=models.PROTECT, null=True, blank=True,
        related_name='reversal',
    )

    class Meta:
        db_table = 'finance_transaction'
        ordering = ['-date']
        indexes = [models.Index(fields=['date'], name='transaction_date_idx'), models.Index(fields=['budget_item'], name='transaction_budget_item_idx')]
        constraints = [
            models.UniqueConstraint(
                fields=['source_fingerprint'],
                condition=~Q(source_fingerprint=''),
                name='finance_transaction_source_fingerprint_unique',
            ),
        ]

    def __str__(self):
        return f'{self.date} {self.description}'

    def clean(self):
        super().clean()
        if not self.budget_item_id:
            raise ValidationError({'budget_item': 'Every new transaction must belong to a budget item.'})
        if self.budget_item_id and not (
            self.budget_item.period.start_date <= self.date <= self.budget_item.period.end_date
        ):
            raise ValidationError({'date': 'Transaction date must belong to the budget item period.'})
        if self.source_staging_transaction_id and self.source_staging_cc_transaction_id:
            raise ValidationError('A transaction can have only one staging source.')


class Transfer(models.Model):
    source_account = models.ForeignKey(Account, on_delete=models.PROTECT, related_name='outgoing_transfers')
    destination_account = models.ForeignKey(Account, on_delete=models.PROTECT, related_name='incoming_transfers')
    date = models.DateField()
    amount = models.DecimalField(max_digits=14, decimal_places=2)
    description = models.CharField(max_length=255, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'finance_transfer'
        ordering = ['-date', '-id']
        constraints = [models.CheckConstraint(check=Q(amount__gt=0), name='finance_transfer_amount_gt_0')]

    def clean(self):
        super().clean()
        if self.source_account_id == self.destination_account_id:
            raise ValidationError('A transfer requires two different accounts.')


class Reconciliation(models.Model):
    account = models.ForeignKey(Account, on_delete=models.PROTECT, related_name='reconciliations')
    statement_date = models.DateField()
    statement_balance = models.DecimalField(max_digits=14, decimal_places=2)
    calculated_balance = models.DecimalField(max_digits=14, decimal_places=2)
    notes = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'finance_reconciliation'
        ordering = ['-statement_date', '-id']
        constraints = [
            models.UniqueConstraint(fields=['account', 'statement_date'], name='finance_reconciliation_account_date_unique'),
        ]


class ImportBatch(models.Model):
    SOURCE_CHOICES = [('BANK', 'Bank statement'), ('CREDIT_CARD', 'Credit card statement'), ('NOTIFICATION', 'Notification')]
    STATUS_CHOICES = [('STAGED', 'Staged'), ('COMMITTED', 'Committed'), ('DISCARDED', 'Discarded')]

    source_type = models.CharField(max_length=20, choices=SOURCE_CHOICES)
    filename = models.CharField(max_length=255, blank=True)
    account_reference = models.CharField(max_length=100, blank=True)
    content_hash = models.CharField(max_length=64, db_index=True)
    imported_at = models.DateTimeField(auto_now_add=True)
    parser_version = models.CharField(max_length=40, default='1')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='STAGED')

    class Meta:
        db_table = 'finance_import_batch'
        ordering = ['-imported_at']

    def __str__(self):
        return self.filename or f'{self.get_source_type_display()} import {self.pk}'


class StagingTransaction(models.Model):
    TYPE_CHOICES = [('IN', 'Income'), ('OUT', 'Expense')]

    source_file    = models.CharField(max_length=255, null=True, blank=True, db_column='source_file')
    batch = models.ForeignKey(ImportBatch, on_delete=models.PROTECT, related_name='staging_transactions', null=True, blank=True)
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
        indexes = [models.Index(fields=['is_processed'], name='staging_processed_idx'), models.Index(fields=['original_date'], name='staging_original_date_idx'), models.Index(fields=['assigned_category'], name='staging_category_idx'), models.Index(fields=['batch', 'is_processed'], name='staging_batch_processed_idx')]

    def __str__(self):
        return f'{self.original_date} {self.description}'


class StagingCCTransaction(models.Model):
    TYPE_CHOICES = [('IN', 'Payment / Credit'), ('OUT', 'Purchase / Charge')]

    source_file          = models.CharField(max_length=255,  null=True, blank=True, db_column='source_file')
    batch = models.ForeignKey(ImportBatch, on_delete=models.PROTECT, related_name='staging_cc_transactions', null=True, blank=True)
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
        indexes = [models.Index(fields=['is_processed'], name='staging_cc_processed_idx'), models.Index(fields=['original_date'], name='staging_cc_original_date_idx'), models.Index(fields=['assigned_category'], name='staging_cc_category_idx'), models.Index(fields=['batch', 'is_processed'], name='staging_cc_batch_processed_idx')]

    def __str__(self):
        return f'[CC] {self.original_date} {self.description}'
