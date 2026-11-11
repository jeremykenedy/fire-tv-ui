    :goto_17
    invoke-virtual {v7, v0}, LN/p;->p(Z)V

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->s()Z

    move-result v0

    if-eqz v0, :cond_37

    const v0, -0x2b42c688

    invoke-virtual {v7, v0}, LN/p;->V(I)V

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->j()Z

    move-result v0

    if-nez v0, :cond_35

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->m()Z

    move-result v0

    if-eqz v0, :cond_35

    if-eqz v39, :cond_36

    :cond_35
    move-object/from16 v3, v48

    goto :goto_18

    :cond_36
    const v0, -0x2b40f54a

    invoke-virtual {v7, v0}, LN/p;->V(I)V

    const/16 v0, 0x15e

    int-to-float v0, v0

    move-object/from16 v3, v48

    invoke-static {v3, v0}, Landroidx/compose/foundation/layout/d;->g(LZ/o;F)LZ/o;

    move-result-object v0

    invoke-static {v7, v0}, Lx/b;->b(LN/p;LZ/o;)V

    const/4 v8, 0x0

    invoke-virtual {v7, v8}, LN/p;->p(Z)V

    const/4 v8, 0x0

    goto :goto_19

    :goto_18
    const v0, -0x2b41ce69

    invoke-virtual {v7, v0}, LN/p;->V(I)V

    const/16 v0, 0x32

    int-to-float v0, v0

    invoke-static {v3, v0}, Landroidx/compose/foundation/layout/d;->g(LZ/o;F)LZ/o;

    move-result-object v0

    invoke-static {v7, v0}, Lx/b;->b(LN/p;LZ/o;)V

    const/4 v8, 0x0

    invoke-virtual {v7, v8}, LN/p;->p(Z)V

    :goto_19
    invoke-virtual {v7, v8}, LN/p;->p(Z)V

    goto/16 :goto_1d

    :cond_37
    move-object/from16 v3, v48

    const v0, -0x2b3fadbb

    invoke-virtual {v7, v0}, LN/p;->V(I)V

    const v0, -0x16526de

    invoke-virtual {v7, v0}, LN/p;->V(I)V

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->q()Z

    move-result v0

    if-eqz v0, :cond_39

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->m()Z

    move-result v0

    if-eqz v0, :cond_39

    if-nez v39, :cond_39

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->f()I

    move-result v0

    add-int/lit8 v0, v0, -0x5

    int-to-float v0, v0

    const/high16 v4, 0x41a00000    # 20.0f

    mul-float/2addr v0, v4

    const/high16 v4, 0x43900000    # 288.0f

    add-float/2addr v0, v4

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->s()Z

    move-result v4

    if-eqz v4, :cond_38

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->f()I

    move-result v4

    add-int/lit8 v4, v4, -0x5

    int-to-float v4, v4

    const/high16 v5, 0x40c00000    # 6.0f

    mul-float/2addr v4, v5

    const/high16 v5, 0x42700000    # 60.0f

    sub-float/2addr v5, v4

    add-float/2addr v0, v5

    :cond_38
    invoke-static {v3, v0}, Landroidx/compose/foundation/layout/d;->g(LZ/o;F)LZ/o;

    move-result-object v0

    invoke-static {v7, v0}, Lx/b;->b(LN/p;LZ/o;)V

    :cond_39
    const/4 v8, 0x0

    invoke-virtual {v7, v8}, LN/p;->p(Z)V

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->q()Z

    move-result v0

    if-eqz v0, :cond_3a

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->m()Z

    move-result v0

    if-eqz v0, :cond_3b

    if-eqz v39, :cond_3a

    goto :goto_1b

    :cond_3a
    :goto_1a
    const/4 v8, 0x0

    goto :goto_1c

    :cond_3b
    :goto_1b
    invoke-virtual/range {v42 .. v42}, LQ3/T3;->f()I

    move-result v0

    add-int/lit8 v0, v0, -0x5

    int-to-float v0, v0

    const/high16 v4, 0x41200000    # 10.0f

    mul-float/2addr v0, v4

    const/4 v4, 0x0

    add-float/2addr v0, v4

    invoke-virtual/range {v42 .. v42}, LQ3/T3;->s()Z

    move-result v5

    if-eqz v5, :cond_3c

    const/high16 v4, 0x423c0000    # 47.0f

    :cond_3c
    add-float/2addr v0, v4

    invoke-static {v3, v0}, Landroidx/compose/foundation/layout/d;->g(LZ/o;F)LZ/o;

    move-result-object v0

    invoke-static {v7, v0}, Lx/b;->b(LN/p;LZ/o;)V

    goto :goto_1a

    :goto_1c
    invoke-virtual {v7, v8}, LN/p;->p(Z)V

    :goto_1d
